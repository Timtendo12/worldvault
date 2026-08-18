package net.moonbowstudios.worldvault.core;

import net.moonbowstudios.worldvault.core.sync.LocalManifest;
import net.moonbowstudios.worldvault.core.sync.SnapshotCopier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotCopierTest {

	private static void write(Path root, String rel, String content) throws IOException {
		Path file = root.resolve(rel);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	@Test
	void copiesAClosedWorldExactly(@TempDir Path tmp) throws IOException {
		Path world = tmp.resolve("world");
		Path staging = tmp.resolve("staging");
		write(world, "level.dat", "root");
		write(world, "region/r.0.0.mca", "chunk");
		write(world, "session.lock", "lock");

		SnapshotCopier.Result result = SnapshotCopier.copy(world, staging);

		assertTrue(result.isConsistent());
		assertEquals(1, result.passes(), "a quiet directory must settle in a single pass");
		assertEquals("root", Files.readString(staging.resolve("level.dat")));
		assertEquals("chunk", Files.readString(staging.resolve("region/r.0.0.mca")));
		assertFalse(Files.exists(staging.resolve("session.lock")),
			"session.lock must never reach the staging copy");
		assertEquals(LocalManifest.of(world).hashes(), LocalManifest.of(staging).hashes());
	}

	@Test
	void replacesWhateverWasInStaging(@TempDir Path tmp) throws IOException {
		Path world = tmp.resolve("world");
		Path staging = tmp.resolve("staging");
		write(world, "level.dat", "new");
		write(staging, "stale/old.mca", "from a previous snapshot");

		SnapshotCopier.copy(world, staging);

		assertFalse(Files.exists(staging.resolve("stale/old.mca")),
			"a leftover file would be uploaded as if it were part of this world");
	}

	@Test
	void recopiesAFileThatChangedDuringTheFirstPass(@TempDir Path tmp) throws IOException {
		Path world = tmp.resolve("world");
		Path staging = tmp.resolve("staging");
		write(world, "region/r.0.0.mca", "original");

		Path region = world.resolve("region/r.0.0.mca");
		Files.writeString(region, "original-but-longer");
		Files.setLastModifiedTime(region,
			java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));

		SnapshotCopier.Result result = SnapshotCopier.copy(world, staging);

		assertTrue(result.isConsistent());
		assertEquals("original-but-longer", Files.readString(staging.resolve("region/r.0.0.mca")));
	}

	@Test
	void toleratesAFileDeletedMidSnapshot(@TempDir Path tmp) throws IOException {
		Path world = tmp.resolve("world");
		Path staging = tmp.resolve("staging");
		write(world, "keep.dat", "keep");
		write(world, "vanishes.dat", "gone soon");
		Files.delete(world.resolve("vanishes.dat"));

		SnapshotCopier.Result result = SnapshotCopier.copy(world, staging);

		assertTrue(result.isConsistent());
		assertTrue(Files.exists(staging.resolve("keep.dat")));
		assertFalse(Files.exists(staging.resolve("vanishes.dat")));
	}
}
