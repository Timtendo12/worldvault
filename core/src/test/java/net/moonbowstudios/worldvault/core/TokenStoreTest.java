package net.moonbowstudios.worldvault.core;

import net.moonbowstudios.worldvault.core.auth.FileTokenStore;
import net.moonbowstudios.worldvault.core.auth.KeychainTokenStore;
import net.moonbowstudios.worldvault.core.auth.TokenSet;
import net.moonbowstudios.worldvault.core.auth.TokenStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenStoreTest {

	private static TokenSet sample() {
		return new TokenSet("access-abc", "refresh-xyz",
			Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS), "files.content.write");
	}

	@Test
	void tokenSetNeverLeaksTheSecretIntoItsStringForm() {
		String text = sample().toString();

		assertFalse(text.contains("access-abc"));
		assertFalse(text.contains("refresh-xyz"));
		assertTrue(text.contains("refreshable=true"));
	}

	@Test
	void tokenSetKnowsWhenToRefreshBeforeItActuallyExpires() {
		TokenSet almostExpired = new TokenSet("a", "r", Instant.now().plusSeconds(30), null);
		TokenSet fresh = new TokenSet("a", "r", Instant.now().plusSeconds(3600), null);

		assertTrue(almostExpired.needsRefresh());
		assertFalse(fresh.needsRefresh());
	}

	@Test
	void refreshKeepsTheOldRefreshTokenWhenTheProviderIssuesNone() {
		TokenSet original = sample();
		TokenSet response = new TokenSet("new-access", null, Instant.now().plusSeconds(3600), null);

		TokenSet merged = original.withRefreshed(response);

		assertEquals("new-access", merged.accessToken());
		assertEquals("refresh-xyz", merged.refreshToken(),
			"Google omits refresh_token on refresh; dropping it would silently unlink the account");
		assertEquals("files.content.write", merged.scope());
	}

	@Test
	void fileStoreRoundTripsAndIsOwnerOnly(@TempDir Path config) throws Exception {
		FileTokenStore store = new FileTokenStore(config);
		store.save("dropbox", sample());

		Optional<TokenSet> loaded = store.load("dropbox");
		assertTrue(loaded.isPresent());
		assertEquals("access-abc", loaded.get().accessToken());
		assertEquals("refresh-xyz", loaded.get().refreshToken());

		Path file = config.resolve("credentials/dropbox.json");
		if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
			Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
			assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
				perms, "the fallback file must not be readable by other accounts");
		}

		store.delete("dropbox");
		assertTrue(store.load("dropbox").isEmpty());
	}

	@Test
	void loadingAnUnknownProviderIsEmptyNotAnError(@TempDir Path config) throws Exception {
		assertTrue(new FileTokenStore(config).load("never-linked").isEmpty());
	}

	@Test
	@EnabledIfSystemProperty(named = "worldvault.keychain.it", matches = "true")
	void osKeychainRoundTrips(@TempDir Path config) throws Exception {
		TokenStore store = new KeychainTokenStore(config);
		assertTrue(store.isAvailable(), "no OS credential store detected: " + store.describe());

		String provider = "selftest";
		try {
			store.save(provider, sample());

			Optional<TokenSet> loaded = store.load(provider);
			assertTrue(loaded.isPresent(), "wrote a credential but could not read it back");
			assertEquals("access-abc", loaded.get().accessToken());
			assertEquals("refresh-xyz", loaded.get().refreshToken());

			store.save(provider, new TokenSet("second-access", "second-refresh",
				Instant.now().plusSeconds(3600), null));
			assertEquals("second-access", store.load(provider).orElseThrow().accessToken());
		} finally {
			store.delete(provider);
		}
		assertTrue(store.load(provider).isEmpty(), "delete left the item behind");
	}
}
