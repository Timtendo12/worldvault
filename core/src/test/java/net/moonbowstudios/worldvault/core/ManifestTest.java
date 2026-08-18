package net.moonbowstudios.worldvault.core;

import net.moonbowstudios.worldvault.core.sync.LocalManifest;
import net.moonbowstudios.worldvault.core.sync.ManifestDiff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestTest {

	private static void write(Path root, String rel, String content) throws IOException {
		Path file = root.resolve(rel);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	@Test
	void hashesEveryFileWithForwardSlashPaths(@TempDir Path world) throws IOException {
		write(world, "level.dat", "root");
		write(world, "region/r.0.0.mca", "chunk");
		write(world, "region/r.0.1.mca", "chunk");

		LocalManifest manifest = LocalManifest.of(world);

		assertEquals(3, manifest.files().size());
		assertTrue(manifest.files().containsKey("region/r.0.0.mca"),
			"paths must be slash-separated so a Windows manifest matches a Linux one");
		assertEquals(manifest.files().get("region/r.0.0.mca").sha256(),
			manifest.files().get("region/r.0.1.mca").sha256());
	}

	@Test
	void excludesSessionLock(@TempDir Path world) throws IOException {
		write(world, "level.dat", "root");
		write(world, "session.lock", "☃");

		LocalManifest manifest = LocalManifest.of(world);

		assertEquals(1, manifest.files().size());
		assertFalse(manifest.files().containsKey("session.lock"),
			"session.lock is the running game's lock and is meaningless on another machine");
	}

	@Test
	void recordsSizesForProgressReporting(@TempDir Path world) throws IOException {
		write(world, "a.dat", "12345");
		write(world, "b.dat", "1234567890");

		assertEquals(15L, LocalManifest.of(world).totalBytes());
	}

	@Test
	void diffAgainstEmptyRemoteUploadsEverything(@TempDir Path world) throws IOException {
		write(world, "level.dat", "root");
		write(world, "region/r.0.0.mca", "chunk");

		ManifestDiff diff = ManifestDiff.between(LocalManifest.of(world), Map.of());

		assertEquals(2, diff.upload().size());
		assertTrue(diff.delete().isEmpty());
		assertEquals(9L, diff.uploadBytes());
	}

	@Test
	void diffSkipsUnchangedFiles(@TempDir Path world) throws IOException {
		write(world, "level.dat", "root");
		write(world, "region/r.0.0.mca", "chunk");
		LocalManifest manifest = LocalManifest.of(world);

		ManifestDiff none = ManifestDiff.between(manifest, manifest.hashes());
		assertTrue(none.isEmpty(), "an unchanged world must transfer nothing");

		write(world, "region/r.0.0.mca", "chunk-modified");
		ManifestDiff one = ManifestDiff.between(LocalManifest.of(world), manifest.hashes());

		assertEquals(java.util.List.of("region/r.0.0.mca"), one.upload());
		assertEquals(14L, one.uploadBytes(), "progress must count only what is actually sent");
	}

	@Test
	void diffDeletesRemoteFilesThatAreGoneLocally(@TempDir Path world) throws IOException {
		write(world, "level.dat", "root");

		ManifestDiff diff = ManifestDiff.between(
			LocalManifest.of(world),
			Map.of("level.dat", LocalManifest.of(world).files().get("level.dat").sha256(),
				"region/r.9.9.mca", "deadbeef"));

		assertTrue(diff.upload().isEmpty());
		assertEquals(java.util.List.of("region/r.9.9.mca"), diff.delete());
	}
}
