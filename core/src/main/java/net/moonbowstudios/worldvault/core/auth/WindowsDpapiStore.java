package net.moonbowstudios.worldvault.core.auth;

import com.sun.jna.platform.win32.Crypt32Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class WindowsDpapiStore {

	private WindowsDpapiStore() {
	}

	static boolean isAvailable() {
		try {
			// round-trip a probe; dpapi is unavailable in some sandboxed contexts
			byte[] probe = "worldvault".getBytes(java.nio.charset.StandardCharsets.UTF_8);
			return java.util.Arrays.equals(probe,
				Crypt32Util.cryptUnprotectData(Crypt32Util.cryptProtectData(probe)));
		} catch (Throwable t) {
			return false;
		}
	}

	static void store(Path file, byte[] secret) throws IOException {
		Files.createDirectories(file.getParent());
		Files.write(file, Crypt32Util.cryptProtectData(secret));
	}

	static byte[] load(Path file) throws IOException {
		if (!Files.exists(file)) {
			return null;
		}
		return Crypt32Util.cryptUnprotectData(Files.readAllBytes(file));
	}

	static void delete(Path file) throws IOException {
		Files.deleteIfExists(file);
	}
}
