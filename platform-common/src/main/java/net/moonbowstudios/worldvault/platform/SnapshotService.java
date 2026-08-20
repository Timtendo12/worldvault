package net.moonbowstudios.worldvault.platform;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.moonbowstudios.worldvault.WorldVaultClient;
import net.moonbowstudios.worldvault.core.sync.SnapshotCopier;
import net.moonbowstudios.worldvault.core.sync.SyncState;
import net.moonbowstudios.worldvault.core.sync.SyncStatusRegistry;
import net.moonbowstudios.worldvault.core.sync.WorldSyncStatus;
import net.moonbowstudios.worldvault.core.util.WorldVaultConfig;
import net.moonbowstudios.worldvault.mixin.MinecraftServerAccessor;

import java.io.IOException;
import java.nio.file.Path;

public final class SnapshotService {

	private static WorldVaultClient mod;

	private static volatile String openLevelId;
	private static volatile Path openLevelPath;
	private static volatile String openDisplayName;

	private static long nextSnapshotDueMillis;
	private static long forceAfterMillis;

	private SnapshotService() {
	}

	public static void register(WorldVaultClient client) {
		mod = client;

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (!(server instanceof IntegratedServer)) {
				return;
			}
			LevelStorageSource.LevelStorageAccess access =
				((MinecraftServerAccessor) server).worldvault$storageSource();
			openLevelId = access.getLevelId();
			openLevelPath = access.getLevelPath(net.minecraft.world.level.storage.LevelResource.ROOT);
			openDisplayName = server.getWorldData().getLevelName();
			scheduleNext();
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			if (!(server instanceof IntegratedServer)) {
				return;
			}
			String levelId = openLevelId;
			Path levelPath = openLevelPath;
			String displayName = openDisplayName;
			openLevelId = null;
			openLevelPath = null;
			openDisplayName = null;

			if (levelId != null && config().syncOnWorldClose) {
				snapshotAndQueue(levelId, displayName, levelPath);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(SnapshotService::onClientTick);

		ClientLifecycleEvents.CLIENT_STOPPING.register(client2 -> client.engine().close());
	}

	private static void onClientTick(Minecraft minecraft) {
		WorldVaultConfig config = config();
		if (!config.syncEnabled || !config.syncWhilePlaying) {
			return;
		}
		String levelId = openLevelId;
		if (levelId == null || config.isPaused(levelId)) {
			return;
		}

		long now = System.currentTimeMillis();
		if (now < nextSnapshotDueMillis) {
			return;
		}

		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null) {
			return;
		}

		boolean paused = server.isPaused();
		boolean overdue = now >= forceAfterMillis;
		if (!paused && !overdue) {
			return;
		}

		Path levelPath = openLevelPath;
		String displayName = openDisplayName;
		scheduleNext();

		server.execute(() -> {
			try {
				server.saveEverything(true, true, false);
			} catch (RuntimeException e) {
				WorldVaultClient.LOGGER.warn("Save before snapshot failed; skipping this cycle.", e);
				return;
			}
			snapshotAndQueue(levelId, displayName, levelPath);
		});
	}

	private static void scheduleNext() {
		long interval = Math.max(1, config().intervalMinutes) * 60_000L;
		nextSnapshotDueMillis = System.currentTimeMillis() + interval;
		forceAfterMillis = nextSnapshotDueMillis + interval;
	}

	private static void snapshotAndQueue(String levelId, String displayName, Path levelPath) {
		if (levelPath == null || mod.engine().provider().isEmpty()) {
			return;
		}
		SyncStatusRegistry.put(levelId, WorldSyncStatus.of(SyncState.QUEUED));

		Thread worker = new Thread(() -> {
			Path staged = mod.engine().stagingRoot().resolve("upload").resolve(levelId);
			try {
				SnapshotCopier.Result result = SnapshotCopier.copy(levelPath, staged);
				if (!result.isConsistent()) {
					WorldVaultClient.LOGGER.warn(
						"Skipping snapshot of '{}': {} file(s) kept changing while copying.",
						levelId, result.unstable().size());
					SyncStatusRegistry.put(levelId,
						WorldSyncStatus.error("world was still being written; will retry"));
					return;
				}
				mod.engine().queueUpload(levelId, displayName, staged);
			} catch (IOException e) {
				WorldVaultClient.LOGGER.error("Could not stage world '{}'", levelId, e);
				SyncStatusRegistry.put(levelId, WorldSyncStatus.error(e.getMessage()));
			}
		}, "WorldVault-snapshot");
		worker.setDaemon(true);
		worker.start();
	}

	public static String openLevelId() {
		return openLevelId;
	}

	/** @return the display name of the open world, or null when none is open. */
	public static String openDisplayName() {
		return openDisplayName;
	}

	/**
	 * Saves and uploads the open world right now, ignoring the per-world pause and "sync while
	 * playing" setting since this is only reached by an explicit request.
	 *
	 * @return false if no singleplayer world is open
	 */
	public static boolean syncNow() {
		String levelId = openLevelId;
		Path levelPath = openLevelPath;
		String displayName = openDisplayName;
		if (levelId == null || levelPath == null) {
			return false;
		}

		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) {
			return false;
		}

		// push the automatic cycle out, so it does not immediately repeat what was just asked for
		scheduleNext();

		server.execute(() -> {
			try {
				server.saveEverything(true, true, false);
			} catch (RuntimeException e) {
				WorldVaultClient.LOGGER.warn("Save before snapshot failed; syncing anyway.", e);
			}
			snapshotAndQueue(levelId, displayName, levelPath);
		});
		return true;
	}

	private static WorldVaultConfig config() {
		return mod.config();
	}
}
