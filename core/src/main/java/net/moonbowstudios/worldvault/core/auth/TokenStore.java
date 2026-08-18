package net.moonbowstudios.worldvault.core.auth;

import java.util.Optional;

public interface TokenStore {

	boolean isAvailable();

	String describe();

	Optional<TokenSet> load(String providerId) throws TokenStoreException;

	void save(String providerId, TokenSet tokens) throws TokenStoreException;

	void delete(String providerId) throws TokenStoreException;

	class TokenStoreException extends Exception {
		public TokenStoreException(String message) {
			super(message);
		}

		public TokenStoreException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
