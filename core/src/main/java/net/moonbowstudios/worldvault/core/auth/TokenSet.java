package net.moonbowstudios.worldvault.core.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record TokenSet(String accessToken, String refreshToken, Instant expiresAt, String scope) {

	private static final Duration SKEW = Duration.ofMinutes(2);

	public TokenSet {
		Objects.requireNonNull(accessToken, "accessToken");
		Objects.requireNonNull(expiresAt, "expiresAt");
	}

	public static TokenSet expiringIn(String accessToken, String refreshToken, long seconds, String scope) {
		return new TokenSet(accessToken, refreshToken, Instant.now().plusSeconds(seconds), scope);
	}

	public boolean needsRefresh() {
		return Instant.now().plus(SKEW).isAfter(expiresAt);
	}

	public boolean canRefresh() {
		return refreshToken != null && !refreshToken.isBlank();
	}

	public TokenSet withRefreshed(TokenSet fresh) {
		return new TokenSet(
			fresh.accessToken(),
			fresh.canRefresh() ? fresh.refreshToken() : this.refreshToken,
			fresh.expiresAt(),
			fresh.scope() != null ? fresh.scope() : this.scope);
	}

	@Override
	public String toString() {
		return "TokenSet[expiresAt=" + expiresAt + ", scope=" + scope
			+ ", refreshable=" + canRefresh() + "]";
	}
}
