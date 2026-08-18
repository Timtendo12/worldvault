package net.moonbowstudios.worldvault.core.auth;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import net.moonbowstudios.worldvault.core.util.Browser;
import net.moonbowstudios.worldvault.core.util.Http;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class OAuthFlow {

	private static final Duration TIMEOUT = Duration.ofMinutes(5);

	private static final String CALLBACK_PATH = "/callback";

	private OAuthFlow() {
	}

	public static TokenSet authorize(ProviderConfig provider) throws OAuthException {
		PkceChallenge pkce = PkceChallenge.generate();
		String state = PkceChallenge.randomState();

		HttpServer server = null;
		try {
			CompletableFuture<Map<String, String>> callback = new CompletableFuture<>();
			server = startLoopbackServer(callback);
			String redirectUri = "http://127.0.0.1:" + server.getAddress().getPort() + CALLBACK_PATH;

			URI authorizeUri = buildAuthorizeUri(provider, pkce, state, redirectUri);
			try {
				Browser.open(authorizeUri);
			} catch (IOException e) {
				throw new OAuthException("Could not open a browser. Open this URL manually:\n"
					+ authorizeUri, e);
			}

			Map<String, String> params = await(callback);

			if (params.containsKey("error")) {
				String description = params.getOrDefault("error_description", params.get("error"));
				throw new OAuthException("Authorization was refused: " + description);
			}
			if (!state.equals(params.get("state"))) {
				throw new OAuthException("Authorization state did not match; the callback was rejected.");
			}

			String code = params.get("code");
			if (code == null || code.isBlank()) {
				throw new OAuthException("Authorization returned no code.");
			}

			return exchange(provider, code, pkce.verifier(), redirectUri);
		} finally {
			if (server != null) {
				server.stop(0);
			}
		}
	}

	public static TokenSet refresh(ProviderConfig provider, TokenSet existing) throws OAuthException {
		if (!existing.canRefresh()) {
			throw new OAuthException("This account has no refresh token; it must be linked again.");
		}

		Map<String, String> form = new LinkedHashMap<>();
		form.put("grant_type", "refresh_token");
		form.put("refresh_token", existing.refreshToken());
		form.put("client_id", provider.clientId());

		try {
			return existing.withRefreshed(toTokenSet(Http.postForm(provider.tokenUrl(), form)));
		} catch (Http.HttpException e) {
			throw new OAuthException("Could not refresh the " + provider.displayName()
				+ " session: " + e.getMessage(), e);
		}
	}

	static URI buildAuthorizeUri(ProviderConfig provider, PkceChallenge pkce, String state,
	                             String redirectUri) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("client_id", provider.clientId());
		params.put("response_type", "code");
		params.put("redirect_uri", redirectUri);
		params.put("scope", provider.scopes());
		params.put("state", state);
		params.put("code_challenge", pkce.challenge());
		params.put("code_challenge_method", PkceChallenge.METHOD);
		params.putAll(provider.extraAuthArgs());

		return URI.create(provider.authorizeUrl() + "?" + Http.formEncode(params));
	}

	private static TokenSet exchange(ProviderConfig provider, String code, String verifier,
	                                 String redirectUri) throws OAuthException {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("grant_type", "authorization_code");
		form.put("code", code);
		form.put("redirect_uri", redirectUri);
		form.put("client_id", provider.clientId());
		form.put("code_verifier", verifier);

		try {
			return toTokenSet(Http.postForm(provider.tokenUrl(), form));
		} catch (Http.HttpException e) {
			throw new OAuthException("Could not complete sign-in with " + provider.displayName()
				+ ": " + e.getMessage(), e);
		}
	}

	static TokenSet toTokenSet(JsonObject json) throws OAuthException {
		if (json == null || !json.has("access_token")) {
			throw new OAuthException("The token endpoint returned no access token.");
		}
		String accessToken = json.get("access_token").getAsString();
		String refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : null;
		long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600L;
		String scope = json.has("scope") ? json.get("scope").getAsString() : null;

		return TokenSet.expiringIn(accessToken, refreshToken, expiresIn, scope);
	}

	private static HttpServer startLoopbackServer(CompletableFuture<Map<String, String>> callback)
		throws OAuthException {
		try {
			// port 0 lets the os pick one, on loopback so nothing off this machine can reach it
			HttpServer server = HttpServer.create(
				new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);

			server.createContext(CALLBACK_PATH, exchange -> {
				Map<String, String> params = Http.parseQuery(exchange.getRequestURI().getRawQuery());
				byte[] body = resultPage(params).getBytes(StandardCharsets.UTF_8);

				exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(body);
				}
				callback.complete(params);
			});

			server.start();
			return server;
		} catch (IOException e) {
			throw new OAuthException("Could not open a local callback port for sign-in.", e);
		}
	}

	private static Map<String, String> await(CompletableFuture<Map<String, String>> callback)
		throws OAuthException {
		try {
			return callback.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			throw new OAuthException("Timed out waiting for the browser sign-in to finish.", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new OAuthException("Sign-in was cancelled.", e);
		} catch (ExecutionException e) {
			throw new OAuthException("Sign-in failed.", e.getCause());
		}
	}

	private static String resultPage(Map<String, String> params) {
		boolean ok = params.containsKey("code") && !params.containsKey("error");
		String heading = ok ? "WorldVault is linked" : "Sign-in failed";
		String detail = ok
			? "You can close this tab and go back to Minecraft."
			: escape(params.getOrDefault("error_description",
				params.getOrDefault("error", "The provider did not say why.")));

		return """
			<!doctype html><meta charset="utf-8"><title>WorldVault</title>
			<style>
			  body{font:16px/1.5 system-ui,sans-serif;display:grid;place-items:center;
			       height:100vh;margin:0;background:#101418;color:#e8eaed}
			  div{max-width:32rem;padding:2rem;text-align:center}
			  h1{font-size:1.35rem;margin:0 0 .5rem}
			  p{margin:0;color:#9aa0a6}
			</style>
			<div><h1>%s</h1><p>%s</p></div>
			""".formatted(escape(heading), detail);
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	public static class OAuthException extends Exception {
		public OAuthException(String message) {
			super(message);
		}

		public OAuthException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
