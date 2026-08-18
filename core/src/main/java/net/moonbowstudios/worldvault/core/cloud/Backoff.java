package net.moonbowstudios.worldvault.core.cloud;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class Backoff {

	private static final Duration BASE = Duration.ofMillis(500);
	private static final Duration CAP = Duration.ofSeconds(60);

	private final int maxAttempts;
	private int attempt;

	public Backoff(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	public boolean hasNext() {
		return attempt < maxAttempts;
	}

	public int attempt() {
		return attempt;
	}

	public void sleep(Duration serverHint) throws InterruptedException {
		attempt++;
		Duration delay;

		if (serverHint != null && !serverHint.isNegative() && !serverHint.isZero()) {
			delay = min(serverHint, CAP);
		} else {
			long millis = Math.min(CAP.toMillis(), BASE.toMillis() * (1L << Math.min(attempt, 20)));
			delay = Duration.ofMillis(ThreadLocalRandom.current().nextLong(millis + 1));
		}

		Thread.sleep(delay.toMillis());
	}

	private static Duration min(Duration a, Duration b) {
		return a.compareTo(b) <= 0 ? a : b;
	}
}
