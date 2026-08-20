package net.moonbowstudios.worldvault.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.moonbowstudios.worldvault.WorldVaultClient;
import net.moonbowstudios.worldvault.core.sync.SnapshotCopier;
import net.moonbowstudios.worldvault.core.sync.SyncStatusRegistry;
import net.moonbowstudios.worldvault.core.sync.WorldSyncStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConflictScreen extends Screen {

	private final Screen parent;
	private final String levelId;
	private final String remoteDeviceName;

	private final TransferWatcher watcher = new TransferWatcher();

	private Component status = Component.empty();
	private StringWidget statusLine;
	private boolean started;

	public ConflictScreen(Screen parent, String levelId, String remoteDeviceName) {
		super(Component.translatable("worldvault.conflict.title"));
		this.parent = parent;
		this.levelId = levelId;
		this.remoteDeviceName = remoteDeviceName;
	}

	@Override
	protected void init() {
		int centre = this.width / 2;
		int wide = 280;
		int y = 40;

		addRenderableWidget(new StringWidget(centre - wide / 2, y, wide, 12, this.title, this.font));
		y += 18;
		addRenderableWidget(new StringWidget(centre - wide / 2, y, wide, 12,
			Component.translatable("worldvault.conflict.explain", levelId,
				remoteDeviceName != null ? remoteDeviceName : "another device"), this.font));
		y += 30;

		addRenderableWidget(choice("worldvault.conflict.keep_local", this::keepLocal,
			centre - wide / 2, y, wide));
		y += 24;

		addRenderableWidget(choice("worldvault.conflict.keep_cloud", this::keepCloud,
			centre - wide / 2, y, wide));
		y += 24;

		addRenderableWidget(choice("worldvault.conflict.keep_both", this::keepBoth,
			centre - wide / 2, y, wide));
		y += 30;

		statusLine = new StringWidget(centre - wide / 2, y, wide, 12, status, this.font);
		addRenderableWidget(statusLine);

		// once work is under way there is nothing left to cancel, so the button just closes
		addRenderableWidget(Button.builder(
			started ? CommonComponents.GUI_DONE : CommonComponents.GUI_CANCEL, b -> onClose())
			.bounds(centre - 100, this.height - 32, 200, 20).build());
	}

	private Button choice(String key, Runnable onPress, int x, int y, int wide) {
		Button button = Button.builder(Component.translatable(key), b -> onPress.run())
			.bounds(x, y, wide, 20).build();
		// begin() rebuilds the widgets, so this is all the disabling that is needed
		button.active = !started;
		return button;
	}

	private void keepLocal() {
		WorldVaultClient mod = WorldVaultClient.get();
		if (this.minecraft == null || mod == null || started) {
			return;
		}
		Path world = this.minecraft.getLevelSource().getLevelPath(levelId);

		begin(levelId, levelId, TransferWatcher.UPLOAD,
			Component.translatable("worldvault.conflict.uploading"));

		runOffThread(() -> {
			Path staged = mod.engine().stagingRoot().resolve("upload").resolve(levelId);
			SnapshotCopier.copy(world, staged);
			mod.engine().queueUpload(levelId, levelId, staged, true);
		});
	}

	private void keepCloud() {
		WorldVaultClient mod = WorldVaultClient.get();
		if (this.minecraft == null || mod == null || started) {
			return;
		}
		Path world = this.minecraft.getLevelSource().getLevelPath(levelId);

		begin(levelId, levelId, TransferWatcher.DOWNLOAD,
			Component.translatable("worldvault.conflict.downloading"));
		mod.engine().queueDownload(levelId, world);
	}

	private void keepBoth() {
		WorldVaultClient mod = WorldVaultClient.get();
		if (this.minecraft == null || mod == null || started) {
			return;
		}
		String suffix = remoteDeviceName != null && !remoteDeviceName.isBlank()
			? remoteDeviceName : "cloud";
		String newId = uniqueLevelId(levelId + " (from " + suffix + ")");
		Path target = this.minecraft.getLevelSource().getLevelPath(newId);

		begin(newId, newId, TransferWatcher.DOWNLOAD,
			Component.translatable("worldvault.conflict.keeping_both", newId));
		mod.engine().queueDownload(levelId, target, newId);
	}

	/**
	 * Starts watching a transfer and keeps the screen open so the result is actually visible,
	 * rather than popping straight back to the parent.
	 */
	private void begin(String watchedLevelId, String displayName, String keyPrefix,
	                   Component initial) {
		started = true;
		watcher.watch(watchedLevelId, displayName, keyPrefix);
		status = initial;
		rebuildWidgets();
	}

	@Override
	public void tick() {
		super.tick();
		if (!watcher.isWatching()) {
			return;
		}

		// leave the chosen action's message up until the engine actually starts moving bytes
		if (!watcher.isPending()) {
			setStatus(watcher.current());
		}

		if (watcher.isTerminal()) {
			watcher.stop();
		}
	}

	private void setStatus(Component next) {
		if (next.equals(status)) {
			return;
		}
		status = next;
		if (statusLine != null) {
			statusLine.setMessage(next);
		}
	}

	private String uniqueLevelId(String preferred) {
		String base = CloudWorldsScreen.safeLevelId(preferred);
		String candidate = base;
		int n = 1;
		while (this.minecraft != null
			&& Files.exists(this.minecraft.getLevelSource().getLevelPath(candidate))) {
			candidate = base + " " + (++n);
		}
		return candidate;
	}

	private void runOffThread(IoTask task) {
		Thread worker = new Thread(() -> {
			try {
				task.run();
			} catch (IOException e) {
				WorldVaultClient.LOGGER.error("Conflict resolution failed for '{}'", levelId, e);
				SyncStatusRegistry.put(levelId, WorldSyncStatus.error(e.getMessage()));
			}
		}, "WorldVault-conflict");
		worker.setDaemon(true);
		worker.start();
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(parent);
		}
	}

	@FunctionalInterface
	private interface IoTask {
		void run() throws IOException;
	}
}
