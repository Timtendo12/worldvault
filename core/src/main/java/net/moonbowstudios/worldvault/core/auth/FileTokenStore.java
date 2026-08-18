package net.moonbowstudios.worldvault.core.auth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public final class FileTokenStore implements TokenStore {

	private static final Set<PosixFilePermission> OWNER_ONLY =
		EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

	private final Path dir;

	public FileTokenStore(Path configDir) {
		this.dir = configDir.resolve("credentials");
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public String describe() {
		return "plain file (not encrypted)";
	}

	@Override
	public Optional<TokenSet> load(String providerId) throws TokenStoreException {
		Path file = fileFor(providerId);
		try {
			if (!Files.exists(file)) {
				return Optional.empty();
			}
			return Optional.of(KeychainTokenStore.deserialise(Files.readAllBytes(file)));
		} catch (IOException e) {
			throw new TokenStoreException("Could not read " + file, e);
		}
	}

	@Override
	public void save(String providerId, TokenSet tokens) throws TokenStoreException {
		Path file = fileFor(providerId);
		try {
			Files.createDirectories(dir);
			restrictPermissions(dir, PosixFilePermission.OWNER_EXECUTE);
			Files.deleteIfExists(file);
			createRestricted(file);
			Files.write(file, KeychainTokenStore.serialise(tokens));
		} catch (IOException e) {
			throw new TokenStoreException("Could not write " + file, e);
		}
	}

	@Override
	public void delete(String providerId) throws TokenStoreException {
		try {
			Files.deleteIfExists(fileFor(providerId));
		} catch (IOException e) {
			throw new TokenStoreException("Could not delete credentials", e);
		}
	}

	private Path fileFor(String providerId) {
		return dir.resolve(providerId + ".json");
	}

	private static void createRestricted(Path file) throws IOException {
		if (supportsPosix(file)) {
			// owner-only from the start; chmod after writing would expose the secret briefly
			Files.createFile(file,
				PosixFilePermissions.asFileAttribute(OWNER_ONLY));
		} else {
			Files.createFile(file);
		}
	}

	private static void restrictPermissions(Path path, PosixFilePermission extra) throws IOException {
		if (!supportsPosix(path)) {
			return;
		}
		Set<PosixFilePermission> perms = EnumSet.copyOf(OWNER_ONLY);
		perms.add(extra);
		Files.setPosixFilePermissions(path, perms);
	}

	private static boolean supportsPosix(Path path) {
		return path.getFileSystem().supportedFileAttributeViews().contains("posix");
	}
}
