package net.moonbowstudios.worldvault.core.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.moonbowstudios.worldvault.core.auth.ProviderConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class WorldVaultConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // generated at build time from secrets.properties; empty in a clone that has none
    private static final Properties BUILT_IN = builtInDefaults();

    public String activeProvider;

    public boolean syncEnabled = true;

    public int intervalMinutes = 15;

    public boolean syncOnWorldClose = true;

    public boolean syncWhilePlaying = true;

    public String deviceId = UUID.randomUUID().toString();

    public String deviceName = "This computer";

    public Set<String> pausedWorlds = new LinkedHashSet<>();

    public boolean allowPlaintextCredentials = false;

    public String dropboxClientId = builtIn("dropbox.clientId");
    public String googleClientId = builtIn("google.clientId");

    public String googleTokenEndpoint = builtIn("google.tokenEndpoint");

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
        if (isBlank(config.dropboxClientId)) {
            config.dropboxClientId = builtIn("dropbox.clientId");
        }
        if (isBlank(config.googleClientId)) {
            config.googleClientId = builtIn("google.clientId");
        }
        if (isBlank(config.googleTokenEndpoint)) {
            config.googleTokenEndpoint = builtIn("google.tokenEndpoint");
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

    private static Properties builtInDefaults() {
        Properties props = new Properties();
        try (InputStream in =
                 WorldVaultConfig.class.getResourceAsStream("/worldvault-clients.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
        }
        return props;
    }

    private static String builtIn(String key) {
        return BUILT_IN.getProperty(key, "").trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public ProviderConfig providerConfig(String providerId) {
        return switch (providerId) {
            case "dropbox" -> dropboxClientId.isBlank() ? null
                    : ProviderConfig.dropbox(dropboxClientId);
            case "googledrive" -> googleClientId.isBlank() || googleTokenEndpoint.isBlank() ? null
                    : ProviderConfig.googleDrive(googleClientId, googleTokenEndpoint);
            default -> null;
        };
    }

    public boolean isPaused(String levelId) {
        return pausedWorlds.contains(levelId);
    }
}
