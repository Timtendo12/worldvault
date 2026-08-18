package net.moonbowstudios.worldvault.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.moonbowstudios.worldvault.WorldVaultClient;
import net.moonbowstudios.worldvault.core.cloud.CloudException;
import net.moonbowstudios.worldvault.core.cloud.RemoteManifest;
import net.moonbowstudios.worldvault.core.sync.SyncState;
import net.moonbowstudios.worldvault.core.sync.SyncStatusRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;

public final class CloudWorldsScreen extends Screen {

	private static final int PAGE_SIZE = 6;
	private static final DateTimeFormatter WHEN =
		DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault());

	private final Screen parent;
	private final List<RemoteManifest> worlds = new ArrayList<>();

	private volatile boolean loading = true;
	private volatile Component message = Component.translatable("worldvault.cloud.loading");
	private int page;

	public CloudWorldsScreen(Screen parent) {
		super(Component.translatable("worldvault.cloud.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int centre = this.width / 2;
		int wide = 280;
		int y = 30;

		addRenderableWidget(new StringWidget(centre - wide / 2, y, wide, 12, this.title, this.font));
		y += 20;

		if (loading || worlds.isEmpty()) {
			addRenderableWidget(new StringWidget(centre - wide / 2, y + 20, wide, 12,
				message, this.font));
		} else {
			int from = page * PAGE_SIZE;
			int to = Math.min(worlds.size(), from + PAGE_SIZE);

			for (int i = from; i < to; i++) {
				RemoteManifest world = worlds.get(i);
				boolean sameVersion = WorldVaultClient.minecraftVersion().equals(world.mcVersion());

				addRenderableWidget(new StringWidget(centre - wide / 2, y + 4, wide - 90, 12,
					Component.literal(world.displayName() + "  §7" + world.mcVersion() + " · "
						+ WHEN.format(Instant.ofEpochSecond(world.savedAt()))), this.font));

				boolean conflicted = SyncStatusRegistry.get(safeLevelId(world.displayName())).state()
					== SyncState.CONFLICT;

				Button action = conflicted
					? Button.builder(Component.translatable("worldvault.cloud.resolve"), b -> {
						if (this.minecraft != null) {
							this.minecraft.setScreenAndShow(new ConflictScreen(this,
								safeLevelId(world.displayName()), world.deviceName()));
						}
					}).bounds(centre + wide / 2 - 84, y, 84, 20).build()
					: Button.builder(Component.translatable("worldvault.cloud.download"),
						b -> download(world)).bounds(centre + wide / 2 - 84, y, 84, 20).build();

				action.active = conflicted || sameVersion;
				addRenderableWidget(action);
				y += 24;
			}

			if (worlds.size() > PAGE_SIZE) {
				addRenderableWidget(Button.builder(Component.literal("<"), b -> {
					page = Math.max(0, page - 1);
					rebuildWidgets();
				}).bounds(centre - 60, this.height - 58, 20, 20).build());

				addRenderableWidget(new StringWidget(centre - 30, this.height - 52, 60, 12,
					Component.literal((page + 1) + " / " + pageCount()), this.font));

				addRenderableWidget(Button.builder(Component.literal(">"), b -> {
					page = Math.min(pageCount() - 1, page + 1);
					rebuildWidgets();
				}).bounds(centre + 40, this.height - 58, 20, 20).build());
			}
		}

		addRenderableWidget(Button.builder(
			Component.translatable("worldvault.cloud.settings"), b -> {
				if (this.minecraft != null) {
					this.minecraft.setScreenAndShow(new WorldVaultSettingsScreen(this));
				}
			}).bounds(centre - 100, this.height - 56, 200, 20).build());

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
			.bounds(centre - 100, this.height - 32, 200, 20).build());

		if (loading) {
			refresh();
		}
	}

	private int pageCount() {
		return Math.max(1, (worlds.size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	private void refresh() {
		Thread worker = new Thread(() -> {
			try {
				List<RemoteManifest> found = WorldVaultClient.get().engine().listRemoteWorlds();
				onMainThread(() -> {
					worlds.clear();
					worlds.addAll(found);
					loading = false;
					message = found.isEmpty()
						? Component.translatable("worldvault.cloud.empty")
						: Component.empty();
					rebuildWidgets();
				});
			} catch (CloudException e) {
				onMainThread(() -> {
					loading = false;
					message = Component.literal(e.getMessage());
					rebuildWidgets();
				});
			}
		}, "WorldVault-cloud-list");
		worker.setDaemon(true);
		worker.start();
	}

	private void download(RemoteManifest world) {
		WorldVaultClient mod = WorldVaultClient.get();
		if (this.minecraft == null) {
			return;
		}

		String levelId = safeLevelId(world.displayName());
		Path target = this.minecraft.getLevelSource().getLevelPath(levelId);

		if (Files.exists(target)) {
			message = Component.translatable("worldvault.cloud.exists", levelId);
			rebuildWidgets();
			return;
		}

		message = Component.translatable("worldvault.cloud.downloading", world.displayName());
		rebuildWidgets();
		mod.engine().queueDownload(levelId, target);
	}

	static String safeLevelId(String displayName) {
		String cleaned = displayName.replaceAll("[/\\\\:*?\"<>|\\u0000-\\u001f]", "_").trim();
		if (cleaned.isEmpty()) {
			cleaned = "World";
		}
		return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
	}

	private void onMainThread(Runnable action) {
		if (this.minecraft != null) {
			this.minecraft.execute(action);
		}
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(parent);
		}
	}
}
