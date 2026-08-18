package net.moonbowstudios.worldvault.core;

import net.moonbowstudios.worldvault.core.auth.KeychainTokenStore;
import net.moonbowstudios.worldvault.core.auth.OAuthFlow;
import net.moonbowstudios.worldvault.core.auth.ProviderConfig;
import net.moonbowstudios.worldvault.core.auth.TokenSet;
import net.moonbowstudios.worldvault.core.auth.TokenStore;
import net.moonbowstudios.worldvault.core.cloud.AccessTokens;
import net.moonbowstudios.worldvault.core.cloud.CloudHttp;
import net.moonbowstudios.worldvault.core.cloud.CloudProvider;
import net.moonbowstudios.worldvault.core.cloud.DropboxProvider;
import net.moonbowstudios.worldvault.core.cloud.GoogleDriveProvider;
import net.moonbowstudios.worldvault.core.cloud.OneDriveProvider;
import net.moonbowstudios.worldvault.core.sync.SnapshotCopier;
import net.moonbowstudios.worldvault.core.sync.SyncEngine;
import net.moonbowstudios.worldvault.core.sync.SyncStateStore;
import net.moonbowstudios.worldvault.core.sync.SyncStatusRegistry;
import net.moonbowstudios.worldvault.core.util.WorldVaultConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class SyncHarnessMain {

	private static final String MC_VERSION = "harness";

	public static void main(String[] args) throws Exception {
		Path configDir = Path.of(System.getProperty("user.home"), ".worldvault-harness");
		Files.createDirectories(configDir);

		WorldVaultConfig config = WorldVaultConfig.load(configDir);
		TokenStore tokenStore = new KeychainTokenStore(configDir);
		System.out.println("Credential store: " + tokenStore.describe());

		if (args.length == 0) {
			usage(configDir);
			return;
		}

		switch (args[0].toLowerCase(Locale.ROOT)) {
			case "link" -> link(config, tokenStore, configDir, arg(args, 1));
			case "upload" -> upload(config, tokenStore, configDir, Path.of(arg(args, 1)));
			case "list" -> list(config, tokenStore, configDir);
			case "download" -> download(config, tokenStore, configDir, arg(args, 1),
				Path.of(arg(args, 2)));
			default -> usage(configDir);
		}
	}

	private static void link(WorldVaultConfig config, TokenStore store, Path configDir,
	                         String providerId) throws Exception {
		ProviderConfig providerConfig = config.providerConfig(providerId);
		if (providerConfig == null) {
			System.err.println("No client id configured for '" + providerId + "'. Edit "
				+ configDir.resolve("config.json"));
			return;
		}

		TokenSet tokens = OAuthFlow.authorize(providerConfig);
		store.save(providerId, tokens);
		config.activeProvider = providerId;
		config.save(configDir);
		System.out.println("Linked " + providerConfig.displayName() + ". " + tokens);
	}

	private static void upload(WorldVaultConfig config, TokenStore store, Path configDir,
	                           Path worldDir) throws Exception {
		try (SyncEngine engine = engine(config, store, configDir)) {
			String levelId = worldDir.getFileName().toString();
			Path staged = engine.stagingRoot().resolve("upload").resolve(levelId);

			SnapshotCopier.Result snapshot = SnapshotCopier.copy(worldDir, staged);
			System.out.printf("Staged %d files (%d bytes) in %d pass(es)%n",
				snapshot.filesCopied(), snapshot.bytesCopied(), snapshot.passes());

			engine.queueUpload(levelId, levelId, staged).get();
			System.out.println("Final state: " + SyncStatusRegistry.get(levelId));
		}
	}

	private static void list(WorldVaultConfig config, TokenStore store, Path configDir)
		throws Exception {
		try (SyncEngine engine = engine(config, store, configDir)) {
			engine.listRemoteWorlds().forEach(manifest -> System.out.printf(
				"%-30s gen=%-4d mc=%-8s device=%s%n",
				manifest.displayName(), manifest.generation(), manifest.mcVersion(),
				manifest.deviceName()));
		}
	}

	private static void download(WorldVaultConfig config, TokenStore store, Path configDir,
	                             String levelId, Path target) throws Exception {
		try (SyncEngine engine = engine(config, store, configDir)) {
			engine.queueDownload(levelId, target).get();
			System.out.println("Final state: " + SyncStatusRegistry.get(levelId));
		}
	}

	private static SyncEngine engine(WorldVaultConfig config, TokenStore store, Path configDir)
		throws Exception {
		ProviderConfig providerConfig = config.providerConfig(config.activeProvider);
		if (providerConfig == null) {
			throw new IllegalStateException("No provider linked. Run: link <dropbox|onedrive|googledrive>");
		}

		CloudHttp http = new CloudHttp(new AccessTokens(providerConfig, store));
		CloudProvider provider = switch (config.activeProvider) {
			case "dropbox" -> new DropboxProvider(http);
			case "onedrive" -> new OneDriveProvider(http);
			case "googledrive" -> new GoogleDriveProvider(http);
			default -> throw new IllegalStateException("Unknown provider " + config.activeProvider);
		};

		SyncEngine engine = new SyncEngine(configDir, config, new SyncStateStore(configDir),
			MC_VERSION);
		engine.setProvider(provider);
		return engine;
	}

	private static String arg(String[] args, int index) {
		if (index >= args.length) {
			throw new IllegalArgumentException("Missing argument " + index);
		}
		return args[index];
	}

	private static void usage(Path configDir) {
		System.out.println("""
			WorldVault harness: exercises the sync path with no game process.

			  link <dropbox|onedrive|googledrive>   open a browser and link an account
			  upload <world directory>              snapshot and upload a world
			  list                                  list the worlds in the cloud
			  download <levelId> <target dir>       download and verify a world

			Config: %s""".formatted(configDir.resolve("config.json")));
	}

	private SyncHarnessMain() {
	}
}
