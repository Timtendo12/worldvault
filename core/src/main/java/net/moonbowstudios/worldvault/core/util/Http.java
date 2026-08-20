package net.moonbowstudios.worldvault.core.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class Http {

	private static final Gson GSON = new Gson();

	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(20))
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();

	private static final HttpClient REDIRECTING_CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(20))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private Http() {
	}

	public static HttpClient client() {
		return CLIENT;
	}

	public static HttpClient redirectingClient() {
		return REDIRECTING_CLIENT;
	}

	public static String formEncode(Map<String, String> fields) {
		return fields.entrySet().stream()
			.filter(e -> e.getValue() != null)
			.map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
			.collect(Collectors.joining("&"));
	}

	public static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	public static JsonObject postForm(String url, Map<String, String> fields) throws HttpException {
		HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(url))
			.timeout(Duration.ofSeconds(60))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(formEncode(fields), StandardCharsets.UTF_8))
			.build();

		HttpResponse<String> response;
		try {
			response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (java.io.IOException e) {
			throw new HttpException(0, "Network error talking to " + url, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new HttpException(0, "Interrupted talking to " + url, e);
		}

		if (response.statusCode() / 100 != 2) {
			throw new HttpException(response.statusCode(), describeOAuthError(response.body()));
		}
		return GSON.fromJson(response.body(), JsonObject.class);
	}

	/**
	 * Posts a form body and discards the response. The explicit timeout matters: {@link #client()}
	 * sets only a connect timeout, so without one a hung endpoint pins the calling thread.
	 */
	public static void postFormDiscarding(String url, Map<String, String> fields, Duration timeout)
		throws java.io.IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(url))
			.timeout(timeout)
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(formEncode(fields), StandardCharsets.UTF_8))
			.build();

		CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
	}

	private static String describeOAuthError(String body) {
		try {
			JsonObject json = GSON.fromJson(body, JsonObject.class);
			if (json != null && json.has("error")) {
				String code = json.get("error").getAsString();
				String detail = json.has("error_description")
					? json.get("error_description").getAsString() : null;
				return detail != null ? code + ": " + detail : code;
			}
		} catch (RuntimeException ignored) {
		}
		return body == null || body.isBlank() ? "(no response body)" : truncate(body);
	}

	private static String truncate(String text) {
		return text.length() <= 500 ? text : text.substring(0, 500) + "…";
	}

	public static Map<String, String> parseQuery(String query) {
		Map<String, String> out = new LinkedHashMap<>();
		if (query == null || query.isBlank()) {
			return out;
		}
		for (String pair : query.split("&")) {
			int eq = pair.indexOf('=');
			if (eq < 0) {
				out.put(decode(pair), "");
			} else {
				out.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
			}
		}
		return out;
	}

	private static String decode(String value) {
		return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

	public static class HttpException extends Exception {
		private final int status;

		public HttpException(int status, String message) {
			super(message);
			this.status = status;
		}

		public HttpException(int status, String message, Throwable cause) {
			super(message, cause);
			this.status = status;
		}

		public int status() {
			return status;
		}

		public boolean isRetryable() {
			return status == 0 || status == 408 || status == 429 || status >= 500;
		}
	}
}
