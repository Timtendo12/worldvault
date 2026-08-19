package net.moonbowstudios.worldvault.core;

import net.moonbowstudios.worldvault.core.sync.ConflictResolver.LastSync;
import net.moonbowstudios.worldvault.core.sync.SyncStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncStateStoreTest {

	@TempDir
	Path dir;

	private static final Map<String, String> HASHES = Map.of("level.dat", "abc123");

	@Test
	void oneProvidersStateIsInvisibleToAnother() throws Exception {
		SyncStateStore store = new SyncStateStore(dir, "dropbox");
		store.record("dropbox", "New World", 4, "device-1", HASHES);

		assertEquals(new LastSync(4, "device-1"), store.lastSync("dropbox", "New World"));
		assertEquals(HASHES, store.lastUploadedHashes("dropbox", "New World"));

		assertEquals(LastSync.NEVER, store.lastSync("googledrive", "New World"));
		assertTrue(store.lastUploadedHashes("googledrive", "New World").isEmpty());
	}

	@Test
	void bothProvidersAreRememberedAcrossReloads() throws Exception {
		SyncStateStore store = new SyncStateStore(dir, "dropbox");
		store.record("dropbox", "New World", 4, "device-1", HASHES);
		store.record("googledrive", "New World", 9, "device-2", Map.of("level.dat", "def456"));

		SyncStateStore reloaded = new SyncStateStore(dir, "dropbox");
		assertEquals(new LastSync(4, "device-1"), reloaded.lastSync("dropbox", "New World"));
		assertEquals(new LastSync(9, "device-2"), reloaded.lastSync("googledrive", "New World"));
	}

	@Test
	void forgettingAWorldDropsItFromEveryProvider() throws Exception {
		SyncStateStore store = new SyncStateStore(dir, "dropbox");
		store.record("dropbox", "New World", 4, "device-1", HASHES);
		store.record("googledrive", "New World", 9, "device-2", HASHES);

		store.forget("New World");

		assertEquals(LastSync.NEVER, store.lastSync("dropbox", "New World"));
		assertEquals(LastSync.NEVER, store.lastSync("googledrive", "New World"));
	}

	@Test
	void aFlatFileIsAttributedToTheLinkedProvider() throws Exception {
		writeLegacyFile();

		SyncStateStore store = new SyncStateStore(dir, "googledrive");

		assertEquals(new LastSync(7, "device-1"), store.lastSync("googledrive", "New World"));
		assertEquals(LastSync.NEVER, store.lastSync("dropbox", "New World"));
		assertEquals(new LastSync(7, "device-1"),
			new SyncStateStore(dir, "dropbox").lastSync("googledrive", "New World"));
	}

	@Test
	void aFlatFileIsDiscardedWhenNothingIsLinked() throws Exception {
		writeLegacyFile();

		SyncStateStore store = new SyncStateStore(dir, null);

		assertEquals(LastSync.NEVER, store.lastSync("googledrive", "New World"));
		assertEquals(LastSync.NEVER, store.lastSync("dropbox", "New World"));
	}

	private void writeLegacyFile() throws Exception {
		Files.writeString(dir.resolve("sync-state.json"), """
			{
			  "New World": {
			    "generation": 7,
			    "deviceId": "device-1",
			    "hashes": { "level.dat": "abc123" }
			  }
			}
			""", StandardCharsets.UTF_8);
	}
}
