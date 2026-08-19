package net.moonbowstudios.worldvault.core.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.moonbowstudios.worldvault.core.sync.ConflictResolver.LastSync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SyncStateStore {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public record WorldState(long generation, String deviceId, Map<String, String> hashes) {
	}

	private final Path file;
	private final Map<String, Map<String, WorldState>> byProvider;

	public SyncStateStore(Path configDir, String activeProvider) throws IOException {
		this.file = configDir.resolve("sync-state.json");
		this.byProvider = read(activeProvider);
	}

	private Map<String, Map<String, WorldState>> read(String activeProvider) throws IOException {
		if (!Files.exists(file)) {
			return new LinkedHashMap<>();
		}
		String json = Files.readString(file, StandardCharsets.UTF_8);
		JsonObject root = GSON.fromJson(json, JsonObject.class);
		if (root == null) {
			return new LinkedHashMap<>();
		}

		if (isLegacyFlatFile(root)) {
			return migrate(root, activeProvider);
		}

		Map<String, Map<String, WorldState>> parsed = GSON.fromJson(root,
			new TypeToken<LinkedHashMap<String, LinkedHashMap<String, WorldState>>>() { }.getType());
		return parsed != null ? parsed : new LinkedHashMap<>();
	}

	private static boolean isLegacyFlatFile(JsonObject root) {
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (entry.getValue().isJsonObject()
				&& entry.getValue().getAsJsonObject().has("generation")) {
				return true;
			}
		}
		return false;
	}

	private Map<String, Map<String, WorldState>> migrate(JsonObject root, String activeProvider)
		throws IOException {
		Map<String, Map<String, WorldState>> migrated = new LinkedHashMap<>();

		if (activeProvider != null) {
			Map<String, WorldState> flat = GSON.fromJson(root,
				new TypeToken<LinkedHashMap<String, WorldState>>() { }.getType());
			if (flat != null && !flat.isEmpty()) {
				migrated.put(activeProvider, flat);
			}
		}

		write(migrated);
		return migrated;
	}

	public synchronized LastSync lastSync(String providerId, String levelId) {
		WorldState state = stateOf(providerId, levelId);
		return state == null ? LastSync.NEVER : new LastSync(state.generation(), state.deviceId());
	}

	public synchronized Map<String, String> lastUploadedHashes(String providerId, String levelId) {
		WorldState state = stateOf(providerId, levelId);
		return state == null || state.hashes() == null ? Map.of() : state.hashes();
	}

	private WorldState stateOf(String providerId, String levelId) {
		Map<String, WorldState> worlds = byProvider.get(providerId);
		return worlds == null ? null : worlds.get(levelId);
	}

	public synchronized void record(String providerId, String levelId, long generation,
	                                String deviceId, Map<String, String> hashes) throws IOException {
		byProvider.computeIfAbsent(providerId, id -> new LinkedHashMap<>())
			.put(levelId, new WorldState(generation, deviceId, new LinkedHashMap<>(hashes)));
		write(byProvider);
	}

	public synchronized void forget(String levelId) throws IOException {
		boolean removed = false;
		for (Map<String, WorldState> worlds : byProvider.values()) {
			removed |= worlds.remove(levelId) != null;
		}
		if (removed) {
			write(byProvider);
		}
	}

	private void write(Map<String, Map<String, WorldState>> states) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, GSON.toJson(states), StandardCharsets.UTF_8);
	}
}
