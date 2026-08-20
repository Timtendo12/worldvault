package net.moonbowstudios.worldvault.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.moonbowstudios.worldvault.WorldVaultClient;
import net.moonbowstudios.worldvault.core.sync.SyncState;
import net.moonbowstudios.worldvault.core.sync.SyncStatusRegistry;
import net.moonbowstudios.worldvault.core.sync.WorldSyncStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns sync state changes into toasts, so a result is visible even after the world list has
 * closed — syncing often finishes once the player is back on the title screen.
 */
public final class SyncToasts {

	private static final long LINGER_MS = 10_000L;

	// one id per kind, so a newer toast of the same kind replaces rather than stacks
	private static final SystemToast.SystemToastId STARTED = new SystemToast.SystemToastId();
	private static final SystemToast.SystemToastId FINISHED = new SystemToast.SystemToastId();
	private static final SystemToast.SystemToastId FAILED = new SystemToast.SystemToastId(LINGER_MS);
	private static final SystemToast.SystemToastId CONFLICTED =
		new SystemToast.SystemToastId(LINGER_MS);

	private static final Map<String, SyncState> LAST_SEEN = new ConcurrentHashMap<>();

	private SyncToasts() {
	}

	public static void register() {
		SyncStatusRegistry.setListener(SyncToasts::onStatusChanged);
	}

	/** Forgets what each world was last doing, so a provider switch cannot report stale news. */
	public static void reset() {
		LAST_SEEN.clear();
	}

	private static void onStatusChanged(String levelId) {
		WorldSyncStatus status = SyncStatusRegistry.get(levelId);
		SyncState state = status.state();

		if (state == SyncState.NOT_LINKED) {
			LAST_SEEN.remove(levelId);
			return;
		}

		SyncState previous = LAST_SEEN.put(levelId, state);
		if (previous == null || previous == state) {
			// a first sighting is just the startup refresh reporting existing state, and an unchanged
			// state is another file in the same sync finishing — neither is toast-worthy
			return;
		}

		String detail = status.message();
		boolean hasDetail = detail != null && !detail.isBlank();

		switch (state) {
			case SYNCING -> show(STARTED,
				Component.translatable("worldvault.toast.syncing.title"),
				Component.translatable("worldvault.toast.syncing", levelId));
			case SYNCED -> show(FINISHED,
				Component.translatable("worldvault.toast.synced.title"),
				hasDetail
					? Component.translatable("worldvault.toast.synced", levelId, detail)
					: Component.translatable("worldvault.toast.synced.unknown", levelId));
			case ERROR -> show(FAILED,
				Component.translatable("worldvault.toast.failed.title"),
				Component.translatable("worldvault.toast.failed", levelId,
					hasDetail ? detail : "unknown error"));
			case CONFLICT -> show(CONFLICTED,
				Component.translatable("worldvault.toast.conflict.title"),
				hasDetail
					? Component.translatable("worldvault.toast.conflict", levelId, detail)
					: Component.translatable("worldvault.toast.conflict.unknown", levelId));
			default -> {
				// QUEUED, LOCAL_ONLY and PAUSED are resting states, not events
			}
		}
	}

	private static void show(SystemToast.SystemToastId id, Component title, Component detail) {
		WorldVaultClient mod = WorldVaultClient.get();
		if (mod == null || !mod.config().showToasts) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}

		// the registry is written from worker threads, so hop to the render thread
		client.execute(() -> {
			ToastManager toasts = ToastBridge.manager();
			if (toasts != null) {
				SystemToast.add(toasts, id, title, detail);
			}
		});
	}
}
