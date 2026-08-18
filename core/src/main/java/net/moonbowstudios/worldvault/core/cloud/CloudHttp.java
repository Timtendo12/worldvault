package net.moonbowstudios.worldvault.core.cloud;

import net.moonbowstudios.worldvault.core.util.Http;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public final class CloudHttp {

	private static final int MAX_ATTEMPTS = 5;

	private final AccessTokens tokens;

	public CloudHttp(AccessTokens tokens) {
		this.tokens = tokens;
	}

	public AccessTokens tokens() {
		return tokens;
	}

	public <T> HttpResponse<T> send(RequestFactory builder, HttpResponse.BodyHandler<T> handler)
		throws CloudException {
		return send(builder, handler, true, false);
	}

	public <T> HttpResponse<T> sendFollowingRedirects(RequestFactory builder,
	                                                  HttpResponse.BodyHandler<T> handler)
		throws CloudException {
		return send(builder, handler, true, true);
	}

	public <T> HttpResponse<T> sendUnauthenticated(RequestFactory builder,
	                                               HttpResponse.BodyHandler<T> handler)
		throws CloudException {
		return send(builder, handler, false, false);
	}

	private <T> HttpResponse<T> send(RequestFactory builder, HttpResponse.BodyHandler<T> handler,
	                                 boolean authorize, boolean followRedirects)
		throws CloudException {
		Backoff backoff = new Backoff(MAX_ATTEMPTS);
		boolean retriedAuth = false;
		CloudException last = null;

		while (true) {
			HttpResponse<T> response;
			try {
				HttpRequest.Builder pending = builder.build();
				if (authorize) {
					pending.header("Authorization", "Bearer " + tokens.bearer());
				}
				HttpRequest request = pending.build();
				response = (followRedirects ? Http.redirectingClient() : Http.client())
					.send(request, handler);
			} catch (IOException e) {
				last = new CloudException("Network error: " + e.getMessage(), e, true);
				response = null;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new CloudException("Transfer interrupted", e);
			}

			if (response != null) {
				int status = response.statusCode();

				// 308 is how a resumable upload session reports progress, not an error
				if (status / 100 == 2 || status == 308) {
					return response;
				}
				if (status == 401 && authorize && !retriedAuth) {
					tokens.invalidate();
					retriedAuth = true;
					continue;
				}
				if (status != 429 && status < 500) {
					throw new CloudException(describe(response), null, false);
				}
				last = new CloudException(describe(response), null, true);
			}

			if (!backoff.hasNext()) {
				throw last != null ? last : new CloudException("Request failed after retries");
			}
			try {
				backoff.sleep(response != null ? retryAfter(response) : null);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new CloudException("Transfer interrupted", e);
			}
		}
	}

	private static Duration retryAfter(HttpResponse<?> response) {
		Optional<String> header = response.headers().firstValue("Retry-After");
		if (header.isEmpty()) {
			return null;
		}
		try {
			return Duration.ofSeconds(Long.parseLong(header.get().trim()));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String describe(HttpResponse<?> response) {
		Object body = response.body();
		String text = body instanceof String s ? s : "";
		if (text.length() > 400) {
			text = text.substring(0, 400) + "…";
		}
		return "HTTP " + response.statusCode()
			+ (text.isBlank() ? "" : ": " + text.replaceAll("\\s+", " "));
	}

	@FunctionalInterface
	public interface RequestFactory {
		HttpRequest.Builder build();
	}
}
