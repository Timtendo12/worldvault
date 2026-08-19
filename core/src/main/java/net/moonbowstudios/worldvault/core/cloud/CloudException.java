package net.moonbowstudios.worldvault.core.cloud;

public class CloudException extends Exception {

	private final int status;
	private final boolean retryable;

	public CloudException(String message) {
		this(message, null, false);
	}

	public CloudException(String message, Throwable cause) {
		this(message, cause, false);
	}

	public CloudException(String message, Throwable cause, boolean retryable) {
		this(message, 0, retryable, cause);
	}

	public CloudException(String message, int status, boolean retryable) {
		this(message, status, retryable, null);
	}

	private CloudException(String message, int status, boolean retryable, Throwable cause) {
		super(message, cause);
		this.status = status;
		this.retryable = retryable;
	}

	public int status() {
		return status;
	}

	public boolean isRetryable() {
		return retryable;
	}
}
