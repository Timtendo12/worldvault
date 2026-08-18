package net.moonbowstudios.worldvault.core.sync;

import net.moonbowstudios.worldvault.core.cloud.CloudException;
import net.moonbowstudios.worldvault.core.cloud.CloudProvider;
import net.moonbowstudios.worldvault.core.cloud.ProgressListener;
import net.moonbowstudios.worldvault.core.cloud.RemoteManifest;
import net.moonbowstudios.worldvault.core.sync.ConflictResolver.Decision;
import net.moonbowstudios.worldvault.core.util.WorldVaultConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class SyncEngine implements AutoCloseable {

	private static final int TRANSFER_THREADS = 4;

	private final Path configDir;
	private final Path stagingRoot;
	private final WorldVaultConfig config;
	private final SyncStateStore stateStore;
	private final String mcVersion;

	private final ExecutorService worldQueue = Executors.newSingleThreadExecutor(
		named("WorldVault-sync"));
	private final ThreadPoolExecutor transfers = new ThreadPoolExecutor(
		TRANSFER_THREADS, TRANSFER_THREADS, 30, TimeUnit.SECONDS,
		new LinkedBlockingQueue<>(), named("WorldVault-transfer"));

	private volatile CloudProvider provider;

	public SyncEngine(Path configDir, WorldVaultConfig config, SyncStateStore stateStore,
	                  String mcVersion) {
		this.configDir = configDir;
		this.stagingRoot = configDir.resolve("staging");
		this.config = config;
		this.stateStore = stateStore;
		this.mcVersion = mcVersion;
		this.transfers.allowCoreThreadTimeOut(true);
	}

	public void setProvider(CloudProvider provider) {
		this.provider = provider;
	}

	public Optional<CloudProvider> provider() {
		return Optional.ofNullable(provider);
	}

	public Future<?> queueUpload(String levelId, String displayName, Path stagedWorld) {
		return queueUpload(levelId, displayName, stagedWorld, false);
	}

	public Future<?> queueUpload(String levelId, String displayName, Path stagedWorld,
	                             boolean force) {
		return worldQueue.submit(() -> {
			try {
				upload(levelId, displayName, stagedWorld, force);
			} catch (Exception e) {
				SyncStatusRegistry.put(levelId, WorldSyncStatus.error(rootMessage(e)));
			}
		});
	}

	public Future<?> queueDownload(String levelId, Path worldDir) {
		return queueDownload(levelId, worldDir, levelId);
	}

	public Future<?> queueDownload(String levelId, Path worldDir, String intoLevelId) {
		return worldQueue.submit(() -> {
			try {
				download(levelId, worldDir, intoLevelId);
			} catch (Exception e) {
				SyncStatusRegistry.put(intoLevelId, WorldSyncStatus.error(rootMessage(e)));
			}
		});
	}

	public Future<?> queueRefresh(List<String> localLevelIds) {
		return worldQueue.submit(() -> {
			try {
				refresh(localLevelIds);
			} catch (Exception e) {
				// a failed listing must not mark every world as broken
			}
		});
	}

	private void upload(String levelId, String displayName, Path stagedWorld, boolean force)
		throws IOException, CloudException {
		CloudProvider cloud = require();
		SyncStatusRegistry.put(levelId, WorldSyncStatus.of(SyncState.SYNCING));

		LocalManifest local = LocalManifest.of(stagedWorld);
		RemoteManifest remote = readManifest(cloud, levelId).orElse(null);

		boolean localChanged = !local.hashes().equals(stateStore.lastUploadedHashes(levelId));
		Decision decision = force ? Decision.UPLOAD : ConflictResolver.decide(remote,
			stateStore.lastSync(levelId), localChanged, config.deviceId, mcVersion);

		switch (decision) {
			case CONFLICT -> {
				SyncStatusRegistry.put(levelId, new WorldSyncStatus(SyncState.CONFLICT, 0f,
					remote != null ? remote.deviceName() : null));
				return;
			}
			case VERSION_MISMATCH -> {
				SyncStatusRegistry.put(levelId, WorldSyncStatus.error(
					"cloud copy is from Minecraft " + (remote != null ? remote.mcVersion() : "?")));
				return;
			}
			case UP_TO_DATE -> {
				SyncStatusRegistry.put(levelId,
					new WorldSyncStatus(SyncState.SYNCED, 1f, cloud.displayName()));
				return;
			}
			case DOWNLOAD -> {
				SyncStatusRegistry.put(levelId, WorldSyncStatus.of(SyncState.QUEUED));
				return;
			}
			default -> { }
		}

		Map<String, String> remoteHashes = remote != null ? remote.files() : Map.of();
		ManifestDiff diff = ManifestDiff.between(local, remoteHashes);
		transferAll(cloud, levelId, stagedWorld, local, diff);

		long generation = (remote != null ? remote.generation() : 0L) + 1;
		RemoteManifest updated = new RemoteManifest(generation, config.deviceId, config.deviceName,
			Instant.now().getEpochSecond(), displayName, mcVersion, local.hashes());

		// written last, so a crash leaves the previous manifest still describing the cloud copy
		cloud.writeSmallFile(levelId + "/" + RemoteManifest.FILE_NAME, updated.toJson());

		stateStore.record(levelId, generation, config.deviceId, local.hashes());
		SyncStatusRegistry.put(levelId, new WorldSyncStatus(SyncState.SYNCED, 1f, cloud.displayName()));
	}

	private void transferAll(CloudProvider cloud, String levelId, Path stagedWorld,
	                         LocalManifest local, ManifestDiff diff) throws CloudException {
		AtomicLong sent = new AtomicLong();
		long total = Math.max(1L, diff.uploadBytes());
		List<Future<?>> pending = new ArrayList<>();

		for (String rel : diff.upload()) {
			long fileSize = local.files().get(rel).size();
			pending.add(transfers.submit(() -> {
				cloud.uploadFile(levelId + "/" + RemoteManifest.WORLD_DIR + "/" + rel,
					stagedWorld.resolve(rel), ProgressListener.NONE);
				long done = sent.addAndGet(fileSize);
				SyncStatusRegistry.put(levelId,
					WorldSyncStatus.syncing((float) done / total, cloud.displayName()));
				return null;
			}));
		}
		awaitAll(pending);

		pending.clear();
		for (String rel : diff.delete()) {
			pending.add(transfers.submit(() -> {
				cloud.deleteFile(levelId + "/" + RemoteManifest.WORLD_DIR + "/" + rel);
				return null;
			}));
		}
		awaitAll(pending);
	}

	private void download(String levelId, Path worldDir, String intoLevelId)
		throws IOException, CloudException {
		CloudProvider cloud = require();
		SyncStatusRegistry.put(intoLevelId, WorldSyncStatus.of(SyncState.SYNCING));

		RemoteManifest remote = readManifest(cloud, levelId)
			.orElseThrow(() -> new CloudException("No cloud copy of " + levelId));

		if (remote.mcVersion() != null && !remote.mcVersion().equals(mcVersion)) {
			throw new CloudException("That world was saved by Minecraft " + remote.mcVersion()
				+ "; this is " + mcVersion + ".");
		}

		Path staged = stagingRoot.resolve("download").resolve(levelId);
		SnapshotCopier.deleteRecursively(staged);
		Files.createDirectories(staged);

		List<Future<?>> pending = new ArrayList<>();
		AtomicLong done = new AtomicLong();
		long total = Math.max(1, remote.files().size());

		for (String rel : remote.files().keySet()) {
			pending.add(transfers.submit(() -> {
				cloud.downloadFile(levelId + "/" + RemoteManifest.WORLD_DIR + "/" + rel,
					staged.resolve(rel), ProgressListener.NONE);
				SyncStatusRegistry.put(intoLevelId, WorldSyncStatus.syncing(
					(float) done.incrementAndGet() / total, cloud.displayName()));
				return null;
			}));
		}
		awaitAll(pending);

		verify(staged, remote);

		SnapshotCopier.deleteRecursively(worldDir);
		Files.createDirectories(worldDir.getParent());
		Files.move(staged, worldDir);

		if (intoLevelId.equals(levelId)) {
			stateStore.record(levelId, remote.generation(), remote.deviceId(), remote.files());
			SyncStatusRegistry.put(levelId,
				new WorldSyncStatus(SyncState.SYNCED, 1f, cloud.displayName()));
		} else {
			SyncStatusRegistry.put(intoLevelId, WorldSyncStatus.LOCAL_ONLY);
		}
	}

	private static void verify(Path staged, RemoteManifest remote) throws IOException, CloudException {
		Map<String, String> actual = LocalManifest.of(staged).hashes();
		for (Map.Entry<String, String> expected : remote.files().entrySet()) {
			String got = actual.get(expected.getKey());
			if (got == null) {
				throw new CloudException("Download incomplete: " + expected.getKey() + " is missing.");
			}
			if (!got.equals(expected.getValue())) {
				throw new CloudException("Download corrupted: " + expected.getKey()
					+ " does not match its recorded hash.");
			}
		}
	}

	private void refresh(List<String> localLevelIds) throws CloudException {
		CloudProvider cloud = require();

		for (String levelId : localLevelIds) {
			if (config.isPaused(levelId)) {
				SyncStatusRegistry.put(levelId, WorldSyncStatus.of(SyncState.PAUSED));
				continue;
			}
			Optional<RemoteManifest> remote = readManifest(cloud, levelId);
			if (remote.isEmpty()) {
				SyncStatusRegistry.put(levelId, WorldSyncStatus.LOCAL_ONLY);
				continue;
			}

			ConflictResolver.LastSync last = stateStore.lastSync(levelId);
			boolean behind = remote.get().generation() > last.generation()
				&& !config.deviceId.equals(remote.get().deviceId());

			SyncStatusRegistry.put(levelId, behind
				? new WorldSyncStatus(SyncState.QUEUED, 0f, remote.get().deviceName())
				: new WorldSyncStatus(SyncState.SYNCED, 1f, cloud.displayName()));
		}
	}

	public List<RemoteManifest> listRemoteWorlds() throws CloudException {
		CloudProvider cloud = require();
		List<RemoteManifest> out = new ArrayList<>();

		for (String levelId : cloud.listWorldFolders()) {
			readManifest(cloud, levelId).ifPresent(out::add);
		}
		return out;
	}

	private Optional<RemoteManifest> readManifest(CloudProvider cloud, String levelId)
		throws CloudException {
		return cloud.readSmallFile(levelId + "/" + RemoteManifest.FILE_NAME)
			.map(RemoteManifest::fromJson);
	}

	private CloudProvider require() throws CloudException {
		CloudProvider current = provider;
		if (current == null) {
			throw new CloudException("No cloud account is linked.");
		}
		return current;
	}

	private static void awaitAll(List<Future<?>> pending) throws CloudException {
		CloudException failure = null;
		for (Future<?> future : pending) {
			try {
				future.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new CloudException("Transfer interrupted", e);
			} catch (java.util.concurrent.ExecutionException e) {
				if (failure == null) {
					failure = e.getCause() instanceof CloudException ce
						? ce : new CloudException(rootMessage(e.getCause()), e.getCause());
				}
			}
		}
		if (failure != null) {
			throw failure;
		}
	}

	private static String rootMessage(Throwable t) {
		Throwable current = t;
		while (current.getMessage() == null && current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
	}

	private static ThreadFactory named(String prefix) {
		return runnable -> {
			Thread thread = new Thread(runnable, prefix);
			thread.setDaemon(true);
			return thread;
		};
	}

	public Path stagingRoot() {
		return stagingRoot;
	}

	public Path configDir() {
		return configDir;
	}

	@Override
	public void close() {
		worldQueue.shutdownNow();
		transfers.shutdownNow();
	}
}
