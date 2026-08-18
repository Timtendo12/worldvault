package net.moonbowstudios.worldvault.core.sync;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class SnapshotCopier {

	public static final int MAX_PASSES = 3;

	public record Result(int filesCopied, long bytesCopied, int passes, List<String> unstable) {
		public boolean isConsistent() {
			return unstable.isEmpty();
		}
	}

	private record Stamp(long size, long modifiedMillis) {
	}

	private SnapshotCopier() {
	}

	public static Result copy(Path source, Path staging) throws IOException {
		deleteRecursively(staging);
		Files.createDirectories(staging);

		Map<String, Stamp> before = stampAll(source);
		int filesCopied = 0;
		long bytesCopied = 0L;
		List<String> pending = new ArrayList<>(before.keySet());
		int pass = 0;

		while (!pending.isEmpty() && pass < MAX_PASSES) {
			pass++;
			for (String rel : pending) {
				Path from = source.resolve(rel);
				if (!Files.exists(from)) {
					continue;
				}
				Path to = staging.resolve(rel);
				Files.createDirectories(to.getParent());
				Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
				filesCopied++;
				bytesCopied += Files.size(to);
			}

			// anything that moved while we were reading it has to be copied again
			Map<String, Stamp> after = stampAll(source);
			List<String> changed = new ArrayList<>();
			for (String rel : pending) {
				Stamp was = before.get(rel);
				Stamp now = after.get(rel);
				if (now != null && !now.equals(was)) {
					changed.add(rel);
				}
			}
			before = after;
			pending = changed;
		}

		pruneRemoved(staging, before.keySet());

		return new Result(filesCopied, bytesCopied, pass, List.copyOf(pending));
	}

	private static Map<String, Stamp> stampAll(Path root) throws IOException {
		Map<String, Stamp> stamps = new LinkedHashMap<>();

		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (attrs.isRegularFile()
					&& !LocalManifest.EXCLUDED_NAMES.contains(file.getFileName().toString())) {
					stamps.put(LocalManifest.relativise(root, file),
						new Stamp(attrs.size(), attrs.lastModifiedTime().toMillis()));
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				return FileVisitResult.CONTINUE;
			}
		});

		return stamps;
	}

	private static void pruneRemoved(Path staging, java.util.Set<String> keep) throws IOException {
		List<Path> stale = new ArrayList<>();

		Files.walkFileTree(staging, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (attrs.isRegularFile() && !keep.contains(LocalManifest.relativise(staging, file))) {
					stale.add(file);
				}
				return FileVisitResult.CONTINUE;
			}
		});

		for (Path path : stale) {
			Files.deleteIfExists(path);
		}
	}

	public static void deleteRecursively(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}
}
