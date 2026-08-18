package net.moonbowstudios.worldvault.core;

import net.moonbowstudios.worldvault.core.auth.PkceChallenge;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PkceTest {

	@Test
	void matchesTheWorkedExampleFromRfc7636() {
		String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
		String expected = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

		assertEquals(expected, PkceChallenge.deriveChallenge(verifier));
	}

	@Test
	void generatesAVerifierWithinTheLengthTheSpecAllows() {
		String verifier = PkceChallenge.generate().verifier();

		assertTrue(verifier.length() >= 43 && verifier.length() <= 128,
			"RFC 7636 requires 43-128 characters, got " + verifier.length());
		assertTrue(verifier.matches("[A-Za-z0-9\\-._~]+"),
			"verifier must be unreserved characters only, got " + verifier);
	}

	@Test
	void usesBase64UrlWithoutPadding() {
		String challenge = PkceChallenge.generate().challenge();

		assertFalse(challenge.contains("="), "padding is not allowed in a code_challenge");
		assertFalse(challenge.contains("+") || challenge.contains("/"),
			"must be base64url, not standard base64");
	}

	@Test
	void everyChallengeIsDistinct() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 500; i++) {
			assertTrue(seen.add(PkceChallenge.generate().verifier()), "verifier repeated");
		}
	}

	@Test
	void stateIsNotDerivedFromTheVerifier() {
		PkceChallenge pkce = PkceChallenge.generate();
		assertNotEquals(pkce.verifier(), PkceChallenge.randomState());
	}
}
