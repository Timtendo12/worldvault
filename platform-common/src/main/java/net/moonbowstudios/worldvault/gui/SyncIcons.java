package net.moonbowstudios.worldvault.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.moonbowstudios.worldvault.core.sync.SyncState;
import net.moonbowstudios.worldvault.core.sync.WorldSyncStatus;

import java.util.EnumMap;
import java.util.Map;

public final class SyncIcons {

	public static final int SIZE = 10;

	private static final int SPINNER_FRAMES = 8;
	private static final long SPINNER_FRAME_MS = 80L;

	private static final Map<SyncState, Identifier> STATIC_ICONS = new EnumMap<>(SyncState.class);
	private static final Identifier[] SPINNER = new Identifier[SPINNER_FRAMES];

	static {
		STATIC_ICONS.put(SyncState.LOCAL_ONLY, sprite("local_only"));
		STATIC_ICONS.put(SyncState.QUEUED, sprite("queued"));
		STATIC_ICONS.put(SyncState.SYNCED, sprite("synced"));
		STATIC_ICONS.put(SyncState.CONFLICT, sprite("conflict"));
		STATIC_ICONS.put(SyncState.ERROR, sprite("error"));
		STATIC_ICONS.put(SyncState.PAUSED, sprite("paused"));

		for (int i = 0; i < SPINNER_FRAMES; i++) {
			SPINNER[i] = sprite("syncing_" + i);
		}
	}

	private SyncIcons() {
	}

	private static Identifier sprite(String name) {
		return Identifier.fromNamespaceAndPath("worldvault", "sync/" + name);
	}

	public static Identifier iconFor(WorldSyncStatus status, long timeMs) {
		if (status.state() == SyncState.SYNCING) {
			int frame = (int) ((timeMs / SPINNER_FRAME_MS) % SPINNER_FRAMES);
			return SPINNER[frame];
		}
		return STATIC_ICONS.get(status.state());
	}

	public static Component tooltipFor(WorldSyncStatus status) {
		String detail = status.message();
		boolean hasDetail = detail != null && !detail.isBlank();

		return switch (status.state()) {
			case NOT_LINKED -> null;
			case LOCAL_ONLY -> Component.translatable("worldvault.state.local_only");
			case QUEUED -> Component.translatable("worldvault.state.queued");
			case SYNCING -> hasDetail
				? Component.translatable("worldvault.state.syncing.to", detail, status.percent())
				: Component.translatable("worldvault.state.syncing", status.percent());
			case SYNCED -> hasDetail
				? Component.translatable("worldvault.state.synced.to", detail)
				: Component.translatable("worldvault.state.synced");
			case CONFLICT -> hasDetail
				? Component.translatable("worldvault.state.conflict.on", detail)
				: Component.translatable("worldvault.state.conflict");
			case ERROR -> Component.translatable("worldvault.state.error",
				hasDetail ? detail : "unknown error");
			case PAUSED -> Component.translatable("worldvault.state.paused");
		};
	}
}
