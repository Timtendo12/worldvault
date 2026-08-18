package net.moonbowstudios.worldvault.core.auth;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

public final class KeychainTokenStore implements TokenStore {

	private static final String SERVICE = "net.moonbowstudios.worldvault";

	private static final Gson GSON = new Gson();

	private enum Backend { MAC, WINDOWS, LINUX, NONE }

	private final Backend backend;
	private final Path windowsDir;

	public KeychainTokenStore(Path configDir) {
		this.windowsDir = configDir.resolve("credentials");
		this.backend = detect();
	}

	private static Backend detect() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		try {
			if (os.contains("mac") || os.contains("darwin")) {
				return MacKeychain.isAvailable() ? Backend.MAC : Backend.NONE;
			}
			if (os.contains("win")) {
				return WindowsDpapiStore.isAvailable() ? Backend.WINDOWS : Backend.NONE;
			}
			return LinuxSecretStore.isAvailable() ? Backend.LINUX : Backend.NONE;
		} catch (Throwable t) {
			return Backend.NONE;
		}
	}

	@Override
	public boolean isAvailable() {
		return backend != Backend.NONE;
	}

	@Override
	public String describe() {
		return switch (backend) {
			case MAC -> "macOS Keychain";
			case WINDOWS -> "Windows DPAPI";
			case LINUX -> "Secret Service (libsecret)";
			case NONE -> "unavailable";
		};
	}

	@Override
	public Optional<TokenSet> load(String providerId) throws TokenStoreException {
		byte[] raw;
		try {
			raw = switch (backend) {
				case MAC -> MacKeychain.load(SERVICE, providerId);
				case WINDOWS -> WindowsDpapiStore.load(blobFor(providerId));
				case LINUX -> LinuxSecretStore.load(SERVICE, providerId);
				case NONE -> null;
			};
		} catch (Exception e) {
			throw new TokenStoreException("Could not read credentials from " + describe(), e);
		}

		if (raw == null) {
			return Optional.empty();
		}
		return Optional.of(deserialise(raw));
	}

	@Override
	public void save(String providerId, TokenSet tokens) throws TokenStoreException {
		byte[] raw = serialise(tokens);
		try {
			switch (backend) {
				case MAC -> MacKeychain.store(SERVICE, providerId, raw);
				case WINDOWS -> WindowsDpapiStore.store(blobFor(providerId), raw);
				case LINUX -> LinuxSecretStore.store(SERVICE, providerId, raw);
				case NONE -> throw new TokenStoreException("No OS credential store is available");
			}
		} catch (TokenStoreException e) {
			throw e;
		} catch (Exception e) {
			throw new TokenStoreException("Could not write credentials to " + describe(), e);
		} finally {
			java.util.Arrays.fill(raw, (byte) 0);
		}
	}

	@Override
	public void delete(String providerId) throws TokenStoreException {
		try {
			switch (backend) {
				case MAC -> MacKeychain.delete(SERVICE, providerId);
				case WINDOWS -> WindowsDpapiStore.delete(blobFor(providerId));
				case LINUX -> LinuxSecretStore.delete(SERVICE, providerId);
				case NONE -> { }
			}
		} catch (Exception e) {
			throw new TokenStoreException("Could not remove credentials from " + describe(), e);
		}
	}

	private Path blobFor(String providerId) {
		return windowsDir.resolve(providerId + ".dpapi");
	}

	private record Stored(String accessToken, String refreshToken, long expiresAtEpochSecond,
	                      String scope) {
	}

	static byte[] serialise(TokenSet tokens) {
		Stored stored = new Stored(tokens.accessToken(), tokens.refreshToken(),
			tokens.expiresAt().getEpochSecond(), tokens.scope());
		return GSON.toJson(stored).getBytes(StandardCharsets.UTF_8);
	}

	static TokenSet deserialise(byte[] raw) throws TokenStoreException {
		try {
			Stored stored = GSON.fromJson(new String(raw, StandardCharsets.UTF_8), Stored.class);
			if (stored == null || stored.accessToken() == null) {
				throw new TokenStoreException("Stored credential is malformed");
			}
			return new TokenSet(stored.accessToken(), stored.refreshToken(),
				Instant.ofEpochSecond(stored.expiresAtEpochSecond()), stored.scope());
		} catch (JsonSyntaxException e) {
			throw new TokenStoreException("Stored credential is malformed", e);
		}
	}
}
