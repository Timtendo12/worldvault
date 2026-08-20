package net.moonbowstudios.worldvault.core.util;

import java.time.Duration;
import java.util.Map;

/**
 * Reports one install and its synced-world count, once per launch. Failure is always silent, so
 * syncing can never be affected by it.
 */
public final class UsagePing {

	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final int MAX_WORLDS = 1_000_000;

	private UsagePing() {
	}

	/** A blank endpoint skips the ping, which is how a build without secrets stays quiet. */
	public static void sendAsync(String endpoint, String pingId, int worlds) {
		if (endpoint == null || endpoint.isBlank() || pingId == null || pingId.isBlank()) {
			return;
		}

		int clamped = Math.clamp(worlds, 0, MAX_WORLDS);
		Thread worker = new Thread(() -> {
			try {
				Http.postFormDiscarding(endpoint,
					Map.of("id", pingId, "worlds", Integer.toString(clamped)), TIMEOUT);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				// a lost ping is not worth a log line
			}
		}, "WorldVault-usage");
		worker.setDaemon(true);
		worker.start();
	}
}
