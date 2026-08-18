package net.moonbowstudios.worldvault.core.sync;

import net.moonbowstudios.worldvault.core.util.Sha256;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public record LocalManifest(Map<String, Entry> files) {

	public static final Set<String> EXCLUDED_NAMES = Set.of("session.lock");

	public record Entry(String sha256, long size) {
	}

	public LocalManifest {
		files = Collections.unmodifiableMap(new TreeMap<>(files));
	}

	public static LocalManifest of(Path worldRoot) throws IOException {
		Map<String, Entry> files = new LinkedHashMap<>();

		Files.walkFileTree(worldRoot, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (!attrs.isRegularFile() || EXCLUDED_NAMES.contains(file.getFileName().toString())) {
					return FileVisitResult.CONTINUE;
				}
				String rel = relativise(worldRoot, file);
				files.put(rel, new Entry(Sha256.ofFile(file), attrs.size()));
				return FileVisitResult.CONTINUE;
			}
		});

		return new LocalManifest(files);
	}

	static String relativise(Path root, Path file) {
		String rel = root.relativize(file).toString();
		return rel.replace(root.getFileSystem().getSeparator(), "/");
	}

	public Map<String, String> hashes() {
		Map<String, String> out = new TreeMap<>();
		files.forEach((path, entry) -> out.put(path, entry.sha256()));
		return out;
	}

	public long totalBytes() {
		return files.values().stream().mapToLong(Entry::size).sum();
	}

	public boolean isEmpty() {
		return files.isEmpty();
	}
}
