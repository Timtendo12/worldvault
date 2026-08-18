package net.moonbowstudios.worldvault.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Sha256 {

	private static final int BUFFER = 1 << 16;

	private Sha256() {
	}

	public static MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	public static String ofFile(Path file) throws IOException {
		MessageDigest digest = newDigest();
		byte[] buffer = new byte[BUFFER];

		try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
			while (in.read(buffer) != -1) {
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	public static String ofBytes(byte[] bytes) {
		return HexFormat.of().formatHex(newDigest().digest(bytes));
	}
}
