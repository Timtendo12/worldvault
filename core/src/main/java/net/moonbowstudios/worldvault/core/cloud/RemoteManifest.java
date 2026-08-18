package net.moonbowstudios.worldvault.core.cloud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.moonbowstudios.worldvault.core.WorldVault;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class RemoteManifest {

	public static final String FILE_NAME = "manifest.json";
	public static final String WORLD_DIR = "world";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private int schema = WorldVault.MANIFEST_SCHEMA;
	private long generation;
	private String deviceId;
	private String deviceName;
	private long savedAt;
	private String displayName;
	private String mcVersion;
	private Map<String, String> files = new TreeMap<>();

	public RemoteManifest() {
	}

	public RemoteManifest(long generation, String deviceId, String deviceName, long savedAt,
	                      String displayName, String mcVersion, Map<String, String> files) {
		this.generation = generation;
		this.deviceId = deviceId;
		this.deviceName = deviceName;
		this.savedAt = savedAt;
		this.displayName = displayName;
		this.mcVersion = mcVersion;
		this.files = new TreeMap<>(files);
	}

	public static RemoteManifest fromJson(byte[] json) {
		RemoteManifest manifest;
		try {
			manifest = GSON.fromJson(new String(json, StandardCharsets.UTF_8), RemoteManifest.class);
		} catch (JsonSyntaxException e) {
			throw new IllegalArgumentException("manifest.json is not valid JSON", e);
		}

		if (manifest == null) {
			throw new IllegalArgumentException("manifest.json is empty");
		}
		if (manifest.schema != WorldVault.MANIFEST_SCHEMA) {
			throw new IllegalArgumentException(
				"manifest.json uses schema " + manifest.schema + ", this build understands "
					+ WorldVault.MANIFEST_SCHEMA);
		}
		if (manifest.files == null) {
			manifest.files = new TreeMap<>();
		}
		return manifest;
	}

	public byte[] toJson() {
		return GSON.toJson(this).getBytes(StandardCharsets.UTF_8);
	}

	public int schema() {
		return schema;
	}

	public long generation() {
		return generation;
	}

	public String deviceId() {
		return deviceId;
	}

	public String deviceName() {
		return deviceName;
	}

	public long savedAt() {
		return savedAt;
	}

	public String displayName() {
		return displayName;
	}

	public String mcVersion() {
		return mcVersion;
	}

	public Map<String, String> files() {
		return Collections.unmodifiableMap(files);
	}
}
