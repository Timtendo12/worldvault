package net.moonbowstudios.worldvault.core.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropboxNotFoundTest {

	private static CloudException error(int status, String body) {
		return new CloudException("HTTP " + status + ": " + body, status, false);
	}

	@Test
	void recognisesAMissingPath() {
		assertTrue(DropboxProvider.isNotFound(
			error(409, "{\"error_summary\": \"path/not_found/.\"}")));
	}

	@Test
	void leavesOtherEndpointErrorsAlone() {
		assertFalse(DropboxProvider.isNotFound(
			error(409, "{\"error_summary\": \"path/conflict/file/.\"}")));
		assertFalse(DropboxProvider.isNotFound(
			error(409, "{\"error_summary\": \"path/insufficient_space/.\"}")));
	}

	@Test
	void ignoresTheSummaryOnAnyOtherStatus() {
		assertFalse(DropboxProvider.isNotFound(error(500, "not_found")));
		assertFalse(DropboxProvider.isNotFound(new CloudException("Network error: timed out")));
	}
}
