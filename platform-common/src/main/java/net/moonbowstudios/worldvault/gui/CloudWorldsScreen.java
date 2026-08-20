package net.moonbowstudios.worldvault.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
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
    private final List<Button> rowActions = new ArrayList<>();
    private final TransferWatcher watcher = new TransferWatcher();

    private volatile boolean loading = true;
    private volatile Component message = Component.translatable("worldvault.cloud.loading");
    private StringWidget statusLine;
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

        rowActions.clear();
        boolean paginated = !loading && worlds.size() > PAGE_SIZE;

        // the page counter rides along in the title so it costs no vertical space
        Component heading = paginated
                ? Component.literal("").append(this.title).append("  §7" + (page + 1) + " / " + pageCount())
                : this.title;
        addRenderableWidget(new StringWidget(centre - wide / 2, y, wide, 12, heading, this.font));
        y += 14;

        // always mounted, so download() and tick() always have somewhere to report
        statusLine = new StringWidget(centre - wide / 2, y, wide, 12, message, this.font);
        addRenderableWidget(statusLine);
        y += 18;

        if (!loading && !worlds.isEmpty()) {
            int from = page * PAGE_SIZE;
            int to = Math.min(worlds.size(), from + PAGE_SIZE);

            for (int i = from; i < to; i++) {
                RemoteManifest world = worlds.get(i);

                addRenderableWidget(new StringWidget(centre - wide / 2, y + 4, wide - 90, 12,
                        Component.literal(world.displayName() + "  §7" + world.mcVersion() + " · "
                                + WHEN.format(Instant.ofEpochSecond(world.savedAt()))), this.font));

                addRenderableWidget(rowAction(world, centre + wide / 2 - 84, y));
                y += 24;
            }
        }

        // the arrows flank the settings button rather than sitting on top of it
        if (paginated) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page = Math.max(0, page - 1);
                rebuildWidgets();
            }).bounds(centre - 150, this.height - 52, 20, 20).build());

            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page = Math.min(pageCount() - 1, page + 1);
                rebuildWidgets();
            }).bounds(centre + 130, this.height - 52, 20, 20).build());
        }

        addRenderableWidget(Button.builder(
                Component.translatable("worldvault.cloud.settings"), b -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreenAndShow(new WorldVaultSettingsScreen(this));
                    }
                }).bounds(centre - 100, this.height - 52, 200, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(centre - 100, this.height - 28, 200, 20).build());

        if (loading) {
            refresh();
        }
    }


    private Button rowAction(RemoteManifest world, int x, int y) {
        String levelId = safeLevelId(world.displayName());
        boolean conflicted = SyncStatusRegistry.get(levelId).state() == SyncState.CONFLICT;
        boolean sameVersion = WorldVaultClient.minecraftVersion().equals(world.mcVersion());

        Button action = conflicted
                ? Button.builder(Component.translatable("worldvault.cloud.resolve"), b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new ConflictScreen(this, levelId,
                        world.deviceName()));
            }
        }).bounds(x, y, 84, 20).build()
                : Button.builder(Component.translatable("worldvault.cloud.download"),
                b -> download(world)).bounds(x, y, 84, 20).build();

        if (!conflicted && !sameVersion) {
            // a disabled button still shows its tooltip, so say why it cannot be clicked
            String savedBy = world.mcVersion() != null ? world.mcVersion() : "an unknown version";
            action.setTooltip(Tooltip.create(Component.translatable(
                    "worldvault.cloud.version_mismatch", savedBy,
                    WorldVaultClient.minecraftVersion())));
        }

        action.active = (conflicted || sameVersion) && !watcher.isWatching();
        rowActions.add(action);
        return action;
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
                    setMessage(found.isEmpty()
                            ? Component.translatable("worldvault.cloud.empty")
                            : Component.empty());
                    rebuildWidgets();
                });
            } catch (CloudException e) {
                onMainThread(() -> {
                    loading = false;
                    setMessage(Component.literal(e.getMessage()));
                    rebuildWidgets();
                });
            }
        }, "WorldVault-cloud-list");
        worker.setDaemon(true);
        worker.start();
    }

    private void download(RemoteManifest world) {
        WorldVaultClient mod = WorldVaultClient.get();
        if (this.minecraft == null || mod == null || watcher.isWatching()) {
            return;
        }

        String levelId = safeLevelId(world.displayName());
        Path target = this.minecraft.getLevelSource().getLevelPath(levelId);

        if (Files.exists(target)) {
            setMessage(Component.translatable("worldvault.cloud.exists", levelId));
            return;
        }

        watcher.watch(levelId, world.displayName(), TransferWatcher.DOWNLOAD);
        setRowActionsActive(false);
        setMessage(Component.translatable("worldvault.cloud.downloading", world.displayName()));
        mod.engine().queueDownload(levelId, target);
    }

    @Override
    public void tick() {
        super.tick();
        if (!watcher.isWatching()) {
            return;
        }

        // leave the opening message up until the engine actually starts moving bytes
        if (!watcher.isPending()) {
            setMessage(watcher.current());
        }

        if (watcher.isTerminal()) {
            watcher.stop();
            // the finished world may now be a local folder, so redraw the rows too
            rebuildWidgets();
        }
    }

    private void setMessage(Component next) {
        if (next.equals(message)) {
            return;
        }
        message = next;
        if (statusLine != null) {
            // text-only change: no relayout, so button hover and focus survive
            statusLine.setMessage(next);
        }
    }

    private void setRowActionsActive(boolean active) {
        for (Button action : rowActions) {
            action.active = active;
        }
    }

    static String safeLevelId(String displayName) {
        String cleaned = displayName.replaceAll("[/\\:*?\"<>|\u0000-\u001f]", "_").trim();
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
