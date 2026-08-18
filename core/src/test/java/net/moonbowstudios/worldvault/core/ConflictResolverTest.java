package net.moonbowstudios.worldvault.core;

import net.moonbowstudios.worldvault.core.cloud.RemoteManifest;
import net.moonbowstudios.worldvault.core.sync.ConflictResolver;
import net.moonbowstudios.worldvault.core.sync.ConflictResolver.Decision;
import net.moonbowstudios.worldvault.core.sync.ConflictResolver.LastSync;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConflictResolverTest {

	private static final String THIS_DEVICE = "device-a";
	private static final String OTHER_DEVICE = "device-b";
	private static final String MC = "26.2";

	private static RemoteManifest remote(long generation, String deviceId) {
		return remote(generation, deviceId, MC);
	}

	private static RemoteManifest remote(long generation, String deviceId, String mcVersion) {
		return new RemoteManifest(generation, deviceId, "Some PC", 0L, "World", mcVersion, Map.of());
	}

	@Test
	void neverUploadedIsANewUpload() {
		assertEquals(Decision.UPLOAD_NEW,
			ConflictResolver.decide(null, LastSync.NEVER, true, THIS_DEVICE, MC));
	}

	@Test
	void nothingChangedAnywhereIsUpToDate() {
		assertEquals(Decision.UP_TO_DATE, ConflictResolver.decide(
			remote(5, THIS_DEVICE), new LastSync(5, THIS_DEVICE), false, THIS_DEVICE, MC));
	}

	@Test
	void localChangesOnOurOwnWorldJustUpload() {
		assertEquals(Decision.UPLOAD, ConflictResolver.decide(
			remote(5, THIS_DEVICE), new LastSync(5, THIS_DEVICE), true, THIS_DEVICE, MC));
	}

	@Test
	void anotherDeviceMovedAheadAndWeAreCleanSoWePull() {
		assertEquals(Decision.DOWNLOAD, ConflictResolver.decide(
			remote(6, OTHER_DEVICE), new LastSync(5, THIS_DEVICE), false, THIS_DEVICE, MC));
	}

	@Test
	void bothSidesMovedIsAConflictAndIsNeverResolvedAutomatically() {
		assertEquals(Decision.CONFLICT, ConflictResolver.decide(
			remote(6, OTHER_DEVICE), new LastSync(5, THIS_DEVICE), true, THIS_DEVICE, MC));
	}

	@Test
	void aRemoteBehindUsIsACrashedUploadToRetryNotAConflict() {
		assertEquals(Decision.UPLOAD, ConflictResolver.decide(
			remote(4, THIS_DEVICE), new LastSync(5, THIS_DEVICE), false, THIS_DEVICE, MC));
	}

	@Test
	void aWorldWeHaveNeverSyncedButExistsInTheCloudPullsWhenClean() {
		assertEquals(Decision.DOWNLOAD, ConflictResolver.decide(
			remote(3, OTHER_DEVICE), LastSync.NEVER, false, THIS_DEVICE, MC));
	}

	@Test
	void aWorldWeHaveNeverSyncedThatAlsoExistsLocallyIsAConflict() {
		assertEquals(Decision.CONFLICT, ConflictResolver.decide(
			remote(3, OTHER_DEVICE), LastSync.NEVER, true, THIS_DEVICE, MC));
	}

	@Test
	void aDifferentMinecraftVersionBlocksEverything() {
		assertEquals(Decision.VERSION_MISMATCH, ConflictResolver.decide(
			remote(6, OTHER_DEVICE, "1.21.11"), new LastSync(5, THIS_DEVICE), false, THIS_DEVICE, MC));
	}

	@Test
	void versionMismatchOutranksAnOtherwiseCleanUpload() {
		assertEquals(Decision.VERSION_MISMATCH, ConflictResolver.decide(
			remote(5, THIS_DEVICE, "1.21.11"), new LastSync(5, THIS_DEVICE), true, THIS_DEVICE, MC));
	}
}
