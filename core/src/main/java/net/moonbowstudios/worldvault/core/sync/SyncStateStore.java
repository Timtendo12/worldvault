package net.moonbowstudios.worldvault.core.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
	private final Map<String, WorldState> states;

	public SyncStateStore(Path configDir) throws IOException {
		this.file = configDir.resolve("sync-state.json");
		this.states = read();
	}

	private Map<String, WorldState> read() throws IOException {
		if (!Files.exists(file)) {
			return new LinkedHashMap<>();
		}
		String json = Files.readString(file, StandardCharsets.UTF_8);
		Map<String, WorldState> parsed = GSON.fromJson(json,
			new TypeToken<LinkedHashMap<String, WorldState>>() { }.getType());
		return parsed != null ? parsed : new LinkedHashMap<>();
	}

	public synchronized LastSync lastSync(String levelId) {
		WorldState state = states.get(levelId);
		return state == null ? LastSync.NEVER : new LastSync(state.generation(), state.deviceId());
	}

	public synchronized Map<String, String> lastUploadedHashes(String levelId) {
		WorldState state = states.get(levelId);
		return state == null || state.hashes() == null ? Map.of() : state.hashes();
	}

	public synchronized void record(String levelId, long generation, String deviceId,
	                                Map<String, String> hashes) throws IOException {
		states.put(levelId, new WorldState(generation, deviceId, new LinkedHashMap<>(hashes)));
		write();
	}

	public synchronized void forget(String levelId) throws IOException {
		if (states.remove(levelId) != null) {
			write();
		}
	}

	private void write() throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, GSON.toJson(states), StandardCharsets.UTF_8);
	}
}
