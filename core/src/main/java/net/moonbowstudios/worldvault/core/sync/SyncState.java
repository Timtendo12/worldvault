package net.moonbowstudios.worldvault.core.sync;

public enum SyncState {
	NOT_LINKED,
	LOCAL_ONLY,
	QUEUED,
	SYNCING,
	SYNCED,
	CONFLICT,
	ERROR,
	PAUSED;

	public boolean isAnimated() {
		return this == SYNCING;
	}

	public boolean isVisible() {
		return this != NOT_LINKED;
	}
}
