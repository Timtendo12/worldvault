package net.moonbowstudios.worldvault.core.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.moonbowstudios.worldvault.core.auth.ProviderConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class WorldVaultConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public String activeProvider;

	public boolean syncEnabled = true;

	public int intervalMinutes = 15;

	public boolean syncOnWorldClose = true;

	public boolean syncWhilePlaying = true;

	public String deviceId = UUID.randomUUID().toString();

	public String deviceName = "This computer";

	public Set<String> pausedWorlds = new LinkedHashSet<>();

	public boolean allowPlaintextCredentials = false;

	// blank so a fork does not share another project's api quota; register your own
	public String dropboxClientId = "";
	public String oneDriveClientId = "";
	public String googleClientId = "";

	public String googleTokenEndpoint = "";

	public static WorldVaultConfig load(Path configDir) throws IOException {
		Path file = file(configDir);
		if (!Files.exists(file)) {
			WorldVaultConfig fresh = new WorldVaultConfig();
			fresh.save(configDir);
			return fresh;
		}

		String json = Files.readString(file, StandardCharsets.UTF_8);
		WorldVaultConfig config = GSON.fromJson(json, WorldVaultConfig.class);
		if (config == null) {
			return new WorldVaultConfig();
		}
		if (config.deviceId == null || config.deviceId.isBlank()) {
			config.deviceId = UUID.randomUUID().toString();
		}
		if (config.pausedWorlds == null) {
			config.pausedWorlds = new LinkedHashSet<>();
		}
		return config;
	}

	public void save(Path configDir) throws IOException {
		Path file = file(configDir);
		Files.createDirectories(file.getParent());
		Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
	}

	private static Path file(Path configDir) {
		return configDir.resolve("config.json");
	}

	public ProviderConfig providerConfig(String providerId) {
		return switch (providerId) {
			case "dropbox" -> dropboxClientId.isBlank() ? null
				: ProviderConfig.dropbox(dropboxClientId);
			case "onedrive" -> oneDriveClientId.isBlank() ? null
				: ProviderConfig.oneDrive(oneDriveClientId);
			case "googledrive" -> googleClientId.isBlank() || googleTokenEndpoint.isBlank() ? null
				: ProviderConfig.googleDrive(googleClientId, googleTokenEndpoint);
			default -> null;
		};
	}

	public boolean isPaused(String levelId) {
		return pausedWorlds.contains(levelId);
	}
}
