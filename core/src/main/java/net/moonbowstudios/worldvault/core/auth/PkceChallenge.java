package net.moonbowstudios.worldvault.core.auth;

import net.moonbowstudios.worldvault.core.util.Sha256;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public record PkceChallenge(String verifier, String challenge) {

	private static final int VERIFIER_BYTES = 32;

	public static final String METHOD = "S256";

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

	public static PkceChallenge generate() {
		byte[] raw = new byte[VERIFIER_BYTES];
		RANDOM.nextBytes(raw);
		String verifier = URL_ENCODER.encodeToString(raw);
		return new PkceChallenge(verifier, deriveChallenge(verifier));
	}

	public static String deriveChallenge(String verifier) {
		byte[] hash = Sha256.newDigest().digest(verifier.getBytes(StandardCharsets.US_ASCII));
		return URL_ENCODER.encodeToString(hash);
	}

	public static String randomState() {
		byte[] raw = new byte[VERIFIER_BYTES];
		RANDOM.nextBytes(raw);
		return URL_ENCODER.encodeToString(raw);
	}
}
