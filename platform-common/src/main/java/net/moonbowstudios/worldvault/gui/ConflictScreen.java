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

	private Component status = Component.empty();

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

		addRenderableWidget(Button.builder(
			Component.translatable("worldvault.conflict.keep_local"), b -> keepLocal())
			.bounds(centre - wide / 2, y, wide, 20).build());
		y += 24;

		addRenderableWidget(Button.builder(
			Component.translatable("worldvault.conflict.keep_cloud"), b -> keepCloud())
			.bounds(centre - wide / 2, y, wide, 20).build());
		y += 24;

		addRenderableWidget(Button.builder(
			Component.translatable("worldvault.conflict.keep_both"), b -> keepBoth())
			.bounds(centre - wide / 2, y, wide, 20).build());
		y += 30;

		addRenderableWidget(new StringWidget(centre - wide / 2, y, wide, 12, status, this.font));

		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
			.bounds(centre - 100, this.height - 32, 200, 20).build());
	}

	private void keepLocal() {
		WorldVaultClient mod = WorldVaultClient.get();
		if (this.minecraft == null) {
			return;
		}
		Path world = this.minecraft.getLevelSource().getLevelPath(levelId);

		runOffThread(() -> {
			Path staged = mod.engine().stagingRoot().resolve("upload").resolve(levelId);
			SnapshotCopier.copy(world, staged);
			mod.engine().queueUpload(levelId, levelId, staged, true);
		});
		close(Component.translatable("worldvault.conflict.uploading"));
	}

	private void keepCloud() {
		if (this.minecraft == null) {
			return;
		}
		Path world = this.minecraft.getLevelSource().getLevelPath(levelId);
		WorldVaultClient.get().engine().queueDownload(levelId, world);
		close(Component.translatable("worldvault.conflict.downloading"));
	}

	private void keepBoth() {
		if (this.minecraft == null) {
			return;
		}
		String suffix = remoteDeviceName != null && !remoteDeviceName.isBlank()
			? remoteDeviceName : "cloud";
		String newId = uniqueLevelId(levelId + " (from " + suffix + ")");
		Path target = this.minecraft.getLevelSource().getLevelPath(newId);

		WorldVaultClient.get().engine().queueDownload(levelId, target, newId);
		close(Component.translatable("worldvault.conflict.keeping_both", newId));
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

	private void close(Component message) {
		status = message;
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(parent);
		}
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
