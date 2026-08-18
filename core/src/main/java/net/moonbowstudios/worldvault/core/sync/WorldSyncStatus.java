package net.moonbowstudios.worldvault.core.sync;

import java.util.Objects;

public record WorldSyncStatus(SyncState state, float progress, String message) {

	public static final WorldSyncStatus NOT_LINKED = new WorldSyncStatus(SyncState.NOT_LINKED, 0f, null);
	public static final WorldSyncStatus LOCAL_ONLY = new WorldSyncStatus(SyncState.LOCAL_ONLY, 0f, null);

	public WorldSyncStatus {
		Objects.requireNonNull(state, "state");
		progress = Math.clamp(progress, 0f, 1f);
	}

	public static WorldSyncStatus of(SyncState state) {
		return new WorldSyncStatus(state, 0f, null);
	}

	public static WorldSyncStatus syncing(float progress, String provider) {
		return new WorldSyncStatus(SyncState.SYNCING, progress, provider);
	}

	public static WorldSyncStatus error(String reason) {
		return new WorldSyncStatus(SyncState.ERROR, 0f, reason);
	}

	public int percent() {
		return Math.round(progress * 100f);
	}
}
