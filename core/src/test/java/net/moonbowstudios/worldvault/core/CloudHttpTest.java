package net.moonbowstudios.worldvault.core;

import com.sun.net.httpserver.HttpServer;
import net.moonbowstudios.worldvault.core.auth.ProviderConfig;
import net.moonbowstudios.worldvault.core.auth.TokenSet;
import net.moonbowstudios.worldvault.core.auth.TokenStore;
import net.moonbowstudios.worldvault.core.cloud.AccessTokens;
import net.moonbowstudios.worldvault.core.cloud.CloudException;
import net.moonbowstudios.worldvault.core.cloud.CloudHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudHttpTest {

	@TempDir
	Path temp;

	private HttpServer server;
	private String baseUrl;
	private final AtomicInteger hits = new AtomicInteger();
	private final List<String> authHeaders = new CopyOnWriteArrayList<>();

	private static final class FixedStore implements TokenStore {
		private TokenSet tokens = new TokenSet("token-1", "refresh-1",
			Instant.now().plusSeconds(3600), null);

		public boolean isAvailable() {
			return true;
		}

		public String describe() {
			return "test";
		}

		public Optional<TokenSet> load(String providerId) {
			return Optional.of(tokens);
		}

		public void save(String providerId, TokenSet t) {
			tokens = t;
		}

		public void delete(String providerId) {
			tokens = null;
		}
	}

	private CloudHttp newClient() {
		ProviderConfig config = new ProviderConfig("test", "Test", "https://example.invalid/auth",
			"https://example.invalid/token", "client", "scope", Map.of());
		return new CloudHttp(new AccessTokens(config, new FixedStore()));
	}

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private void respond(String path, java.util.function.IntSupplier statusForHit,
	                     Map<String, String> headers) {
		respond(path, statusForHit, headers, "{}");
	}

	private void respond(String path, java.util.function.IntSupplier statusForHit,
	                     Map<String, String> headers, String responseBody) {
		server.createContext(path, exchange -> {
			hits.incrementAndGet();
			authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
			headers.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));

			byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(statusForHit.getAsInt(), body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
	}

	private HttpResponse<String> get(String path) throws CloudException {
		return newClient().send(
			() -> HttpRequest.newBuilder(URI.create(baseUrl + path)),
			HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void attachesTheBearerToken() throws Exception {
		respond("/ok", () -> 200, Map.of());

		assertEquals(200, get("/ok").statusCode());
		assertEquals("Bearer token-1", authHeaders.get(0));
	}

	@Test
	void retriesA429AndHonoursRetryAfter() throws Exception {
		respond("/limited", () -> hits.get() <= 2 ? 429 : 200, Map.of("Retry-After", "1"));

		long start = System.nanoTime();
		assertEquals(200, get("/limited").statusCode());
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;

		assertEquals(3, hits.get());
		assertTrue(elapsedMs >= 2_000,
			"two Retry-After: 1 responses must produce at least 2s of waiting, waited " + elapsedMs);
	}

	@Test
	void retriesServerErrors() throws Exception {
		respond("/flaky", () -> hits.get() == 1 ? 503 : 200, Map.of());

		assertEquals(200, get("/flaky").statusCode());
		assertEquals(2, hits.get());
	}

	@Test
	void doesNotRetryAClientError() {
		respond("/bad", () -> 400, Map.of());

		CloudException e = assertThrows(CloudException.class, () -> get("/bad"));
		assertEquals(1, hits.get(), "a 400 will never succeed on retry; retrying just wastes time");
		assertTrue(e.getMessage().startsWith("HTTP 400"));
		assertEquals(400, e.status());
	}

	@Test
	void treats308AsSuccessBecauseResumableUploadsUseIt() throws Exception {
		respond("/chunk", () -> 308, Map.of("Range", "bytes=0-1048575"));

		assertEquals(308, get("/chunk").statusCode());
		assertEquals(1, hits.get());
	}

	@Test
	void keepsTheErrorBodyWhenItWasReadAsBytes() {
		respond("/gone", () -> 409, Map.of(),
			"{\"error_summary\": \"path/not_found/.\"}");

		CloudException e = assertThrows(CloudException.class, () -> newClient().send(
			() -> HttpRequest.newBuilder(URI.create(baseUrl + "/gone")),
			HttpResponse.BodyHandlers.ofByteArray()));

		assertEquals(409, e.status());
		assertTrue(e.getMessage().contains("path/not_found"), e.getMessage());
	}

	@Test
	void keepsTheErrorBodyWhenItWasWrittenToAFile() {
		respond("/gone-download", () -> 409, Map.of(),
			"{\"error_summary\": \"path/not_found/.\"}");

		CloudException e = assertThrows(CloudException.class, () -> newClient().send(
			() -> HttpRequest.newBuilder(URI.create(baseUrl + "/gone-download")),
			HttpResponse.BodyHandlers.ofFile(temp.resolve("body.part"))));

		assertEquals(409, e.status());
		assertTrue(e.getMessage().contains("path/not_found"), e.getMessage());
	}

	@Test
	void retriesOnceAfterA401InCaseTheTokenWentStaleEarly() {
		respond("/expired", () -> 401, Map.of());

		assertThrows(CloudException.class, () -> get("/expired"));
		assertEquals(2, hits.get(), "one re-auth attempt, then give up rather than loop");
	}
}
