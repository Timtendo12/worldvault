package net.moonbowstudios.worldvault;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.moonbowstudios.worldvault.core.auth.FileTokenStore;
import net.moonbowstudios.worldvault.core.auth.KeychainTokenStore;
import net.moonbowstudios.worldvault.core.auth.ProviderConfig;
import net.moonbowstudios.worldvault.core.auth.TokenStore;
import net.moonbowstudios.worldvault.core.cloud.AccessTokens;
import net.moonbowstudios.worldvault.core.cloud.CloudHttp;
import net.moonbowstudios.worldvault.core.cloud.CloudProvider;
import net.moonbowstudios.worldvault.core.cloud.DropboxProvider;
import net.moonbowstudios.worldvault.core.cloud.GoogleDriveProvider;
import net.moonbowstudios.worldvault.core.sync.SyncEngine;
import net.moonbowstudios.worldvault.core.sync.SyncStateStore;
import net.moonbowstudios.worldvault.core.util.WorldVaultConfig;
import net.moonbowstudios.worldvault.platform.SnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class WorldVaultClient implements ClientModInitializer {

	public static final String MOD_ID = "worldvault";
	public static final Logger LOGGER = LoggerFactory.getLogger("WorldVault");

	private static WorldVaultClient instance;

	private Path configDir;
	private WorldVaultConfig config;
	private SyncStateStore stateStore;
	private SyncEngine engine;
	private TokenStore tokenStore;

	public static WorldVaultClient get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);

		try {
			config = WorldVaultConfig.load(configDir);
			stateStore = new SyncStateStore(configDir);
		} catch (IOException e) {
			LOGGER.error("WorldVault could not read its configuration; syncing is off.", e);
			config = new WorldVaultConfig();
			config.syncEnabled = false;
			return;
		}

		tokenStore = chooseTokenStore();
		engine = new SyncEngine(configDir, config, stateStore, minecraftVersion());
		reloadProvider();

		SnapshotService.register(this);
		LOGGER.info("WorldVault ready. Credentials: {}. Provider: {}.",
			tokenStore.describe(), config.activeProvider != null ? config.activeProvider : "none");
	}

	private TokenStore chooseTokenStore() {
		KeychainTokenStore keychain = new KeychainTokenStore(configDir);
		if (keychain.isAvailable()) {
			return keychain;
		}
		if (config.allowPlaintextCredentials) {
			LOGGER.warn("No OS credential store found; using the plaintext fallback as configured.");
			return new FileTokenStore(configDir);
		}
		LOGGER.warn("No OS credential store found. Linking an account is disabled until the "
			+ "plaintext fallback is enabled in WorldVault's settings.");
		return keychain;
	}

	public void reloadProvider() {
		engine.setProvider(null);
		if (!config.syncEnabled || config.activeProvider == null) {
			return;
		}

		ProviderConfig providerConfig = config.providerConfig(config.activeProvider);
		if (providerConfig == null) {
			LOGGER.warn("Provider '{}' has no client id configured; see README.md.",
				config.activeProvider);
			return;
		}

		CloudHttp http = new CloudHttp(new AccessTokens(providerConfig, tokenStore));
		CloudProvider provider = switch (config.activeProvider) {
			case "dropbox" -> new DropboxProvider(http);
			case "googledrive" -> new GoogleDriveProvider(http);
			default -> null;
		};
		engine.setProvider(provider);
	}

	public void saveConfig() {
		try {
			config.save(configDir);
		} catch (IOException e) {
			LOGGER.error("Could not save WorldVault settings", e);
		}
	}

	public static String minecraftVersion() {
		return SharedConstants.getCurrentVersion().name();
	}

	public Path configDir() {
		return configDir;
	}

	public WorldVaultConfig config() {
		return config;
	}

	public SyncEngine engine() {
		return engine;
	}

	public SyncStateStore stateStore() {
		return stateStore;
	}

	public TokenStore tokenStore() {
		return tokenStore;
	}
}
