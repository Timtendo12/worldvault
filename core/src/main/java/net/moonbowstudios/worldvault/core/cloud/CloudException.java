package net.moonbowstudios.worldvault.core.cloud;

public class CloudException extends Exception {

	private final boolean retryable;

	public CloudException(String message) {
		this(message, null, false);
	}

	public CloudException(String message, Throwable cause) {
		this(message, cause, false);
	}

	public CloudException(String message, Throwable cause, boolean retryable) {
		super(message, cause);
		this.retryable = retryable;
	}

	public boolean isRetryable() {
		return retryable;
	}
}
