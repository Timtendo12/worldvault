package net.moonbowstudios.worldvault.gui;

import net.minecraft.network.chat.Component;
import net.moonbowstudios.worldvault.core.sync.SyncState;
import net.moonbowstudios.worldvault.core.sync.SyncStatusRegistry;
import net.moonbowstudios.worldvault.core.sync.WorldSyncStatus;

/**
 * Follows one level id through {@link SyncStatusRegistry} and turns its status into a line of
 * text. Screens poll this from {@code tick()} rather than using
 * {@link SyncStatusRegistry#setListener}, since polling needs no unregistration.
 */
final class TransferWatcher {

	static final String DOWNLOAD = "worldvault.transfer.download";
	static final String UPLOAD = "worldvault.transfer.upload";

	private String levelId;
	private String displayName;
	private String keyPrefix;

	/**
	 * @param keyPrefix {@link #DOWNLOAD} or {@link #UPLOAD}, picking how the progress reads
	 */
	void watch(String levelId, String displayName, String keyPrefix) {
		this.levelId = levelId;
		this.displayName = displayName;
		this.keyPrefix = keyPrefix;

		// claim the slot up front: the engine only marks SYNCING once the job reaches the front of
		// its queue, and a stale SYNCED from an earlier refresh may still be sitting here
		SyncStatusRegistry.put(levelId, WorldSyncStatus.of(SyncState.QUEUED));
	}

	boolean isWatching() {
		return levelId != null;
	}

	/** True while the job is still sitting in the engine queue and has not moved any bytes yet. */
	boolean isPending() {
		return levelId != null && SyncStatusRegistry.get(levelId).state() == SyncState.QUEUED;
	}

	/** True once the transfer has finished, either way. Only meaningful while watching. */
	boolean isTerminal() {
		if (levelId == null) {
			return false;
		}
		return switch (SyncStatusRegistry.get(levelId).state()) {
			// LOCAL_ONLY is what a download into a different level id settles on
			case SYNCED, LOCAL_ONLY, ERROR, CONFLICT -> true;
			default -> false;
		};
	}

	Component current() {
		if (levelId == null) {
			return Component.empty();
		}
		WorldSyncStatus status = SyncStatusRegistry.get(levelId);

		return switch (status.state()) {
			case SYNCING -> Component.translatable(keyPrefix + ".progress",
				displayName, status.percent());
			case SYNCED, LOCAL_ONLY -> Component.translatable(keyPrefix + ".done", displayName);
			case ERROR -> Component.translatable(keyPrefix + ".failed",
				status.message() != null ? status.message() : "unknown error");
			case CONFLICT -> Component.translatable("worldvault.state.conflict");
			default -> Component.translatable(keyPrefix + ".queued");
		};
	}

	void stop() {
		levelId = null;
		displayName = null;
		keyPrefix = null;
	}
}
