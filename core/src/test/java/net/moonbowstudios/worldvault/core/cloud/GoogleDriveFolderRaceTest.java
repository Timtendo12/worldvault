package net.moonbowstudios.worldvault.core.cloud;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.moonbowstudios.worldvault.core.auth.ProviderConfig;
import net.moonbowstudios.worldvault.core.auth.TokenSet;
import net.moonbowstudios.worldvault.core.auth.TokenStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleDriveFolderRaceTest {

	private static final int THREADS = 4;
	private static final long SEARCH_DELAY_MILLIS = 150;

	private static final String FOLDER_MIME = "application/vnd.google-apps.folder";
	private static final Pattern NAME = Pattern.compile("name = '([^']*)'");
	private static final Pattern PARENT = Pattern.compile("'([^']*)' in parents");
	private static final Pattern JSON_NAME = Pattern.compile("\"name\":\"([^\"]*)\"");
	private static final Pattern JSON_PARENT = Pattern.compile("\"parents\":\\[\"([^\"]*)\"\\]");

	private HttpServer server;
	private String baseUrl;

	private final Map<String, String> folders = new ConcurrentHashMap<>();
	private final List<String> createLog = new CopyOnWriteArrayList<>();
	private final AtomicInteger ids = new AtomicInteger();

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

	private GoogleDriveProvider newProvider() {
		ProviderConfig config = new ProviderConfig("googledrive", "Google Drive",
			"https://example.invalid/auth", "https://example.invalid/token", "client", "scope",
			Map.of());
		CloudHttp http = new CloudHttp(new AccessTokens(config, new FixedStore()));
		return new GoogleDriveProvider(http, baseUrl + "/drive/v3", baseUrl + "/upload/drive/v3");
	}

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.setExecutor(Executors.newFixedThreadPool(THREADS * 2));

		server.createContext("/drive/v3/files", this::handleMetadata);
		server.createContext("/upload/drive/v3/files", this::handleUpload);
		server.start();
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private void handleMetadata(HttpExchange exchange) throws IOException {
		if ("POST".equals(exchange.getRequestMethod())) {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String name = extract(body, JSON_NAME);
			String parent = extract(body, JSON_PARENT);
			String key = parent + "/" + name;

			createLog.add(key);
			String id = "folder-" + ids.incrementAndGet();
			folders.putIfAbsent(key, id);
			reply(exchange, "{\"id\":\"" + folders.get(key) + "\"}");
			return;
		}

		String query = URLDecoder.decode(rawQuery(exchange), StandardCharsets.UTF_8);
		boolean wantsFolder = query.contains("mimeType = '" + FOLDER_MIME + "'");
		String name = extract(query, NAME);
		String parent = extract(query, PARENT);

		sleep(SEARCH_DELAY_MILLIS);

		String id = wantsFolder ? folders.get(parent + "/" + name) : null;
		reply(exchange, id == null
			? "{\"files\":[]}"
			: "{\"files\":[{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}]}");
	}

	private void handleUpload(HttpExchange exchange) throws IOException {
		exchange.getRequestBody().readAllBytes();
		reply(exchange, "{\"id\":\"file-" + ids.incrementAndGet() + "\"}");
	}

	@Test
	void concurrentUploadsIntoOneFolderCreateThatFolderExactlyOnce() throws Exception {
		GoogleDriveProvider drive = newProvider();
		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		CountDownLatch ready = new CountDownLatch(THREADS);
		CountDownLatch go = new CountDownLatch(1);
		List<Throwable> failures = new CopyOnWriteArrayList<>();

		for (int i = 0; i < THREADS; i++) {
			String path = "Test!/world/region/r.0." + i + ".mca";
			pool.execute(() -> {
				ready.countDown();
				try {
					go.await();
					drive.writeSmallFile(path, new byte[] {1, 2, 3});
				} catch (Throwable t) {
					failures.add(t);
				}
			});
		}

		assertTrue(ready.await(10, TimeUnit.SECONDS), "threads did not start");
		go.countDown();
		pool.shutdown();
		assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "uploads did not finish");
		assertTrue(failures.isEmpty(), "uploads failed: " + failures);

		assertEquals(List.of("root/WorldVault", "folder-1/Test!", "folder-2/world",
			"folder-3/region"), createLog,
			"each folder must be created exactly once, in root-to-leaf order");
	}

	@Test
	void aFolderAlreadyInDriveIsReusedRatherThanCreatedAgain() throws Exception {
		folders.put("root/WorldVault", "existing-root");
		folders.put("existing-root/Test!", "existing-world");

		GoogleDriveProvider drive = newProvider();
		drive.writeSmallFile("Test!/manifest.json", new byte[] {1});

		assertEquals(List.of(), createLog);
	}

	private static String rawQuery(HttpExchange exchange) {
		String query = exchange.getRequestURI().getRawQuery();
		if (query == null) {
			return "";
		}
		for (String part : query.split("&")) {
			if (part.startsWith("q=")) {
				return part.substring(2);
			}
		}
		return "";
	}

	private static String extract(String text, Pattern pattern) {
		Matcher matcher = pattern.matcher(text);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static void reply(HttpExchange exchange, String json) throws IOException {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
