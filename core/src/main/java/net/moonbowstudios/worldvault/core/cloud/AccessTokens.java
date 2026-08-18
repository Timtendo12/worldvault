package net.moonbowstudios.worldvault.core.cloud;

import net.moonbowstudios.worldvault.core.auth.OAuthFlow;
import net.moonbowstudios.worldvault.core.auth.ProviderConfig;
import net.moonbowstudios.worldvault.core.auth.TokenSet;
import net.moonbowstudios.worldvault.core.auth.TokenStore;

import java.util.Optional;

public final class AccessTokens {

	private final ProviderConfig config;
	private final TokenStore store;

	private TokenSet cached;

	public AccessTokens(ProviderConfig config, TokenStore store) {
		this.config = config;
		this.store = store;
	}

	public ProviderConfig config() {
		return config;
	}

	public synchronized boolean isLinked() {
		try {
			return current().isPresent();
		} catch (CloudException e) {
			return false;
		}
	}

	public synchronized String bearer() throws CloudException {
		TokenSet tokens = current().orElseThrow(
			() -> new CloudException(config.displayName() + " is not linked."));

		if (!tokens.needsRefresh()) {
			return tokens.accessToken();
		}
		if (!tokens.canRefresh()) {
			throw new CloudException(config.displayName()
				+ " session expired and cannot be renewed. Link the account again.");
		}

		try {
			TokenSet refreshed = OAuthFlow.refresh(config, tokens);
			store.save(config.id(), refreshed);
			cached = refreshed;
			return refreshed.accessToken();
		} catch (OAuthFlow.OAuthException | TokenStore.TokenStoreException e) {
			throw new CloudException("Could not renew the " + config.displayName()
				+ " session: " + e.getMessage(), e);
		}
	}

	public synchronized void invalidate() {
		cached = null;
	}

	private Optional<TokenSet> current() throws CloudException {
		if (cached != null) {
			return Optional.of(cached);
		}
		try {
			Optional<TokenSet> loaded = store.load(config.id());
			loaded.ifPresent(tokens -> cached = tokens);
			return loaded;
		} catch (TokenStore.TokenStoreException e) {
			throw new CloudException("Could not read stored credentials: " + e.getMessage(), e);
		}
	}
}
