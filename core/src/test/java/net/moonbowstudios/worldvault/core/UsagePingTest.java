package net.moonbowstudios.worldvault.core;

import com.sun.net.httpserver.HttpServer;
import net.moonbowstudios.worldvault.core.util.UsagePing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Asserts the exact bytes the Worker has to parse, against a loopback server. */
class UsagePingTest {

	private HttpServer server;
	private String url;
	private final AtomicReference<String> body = new AtomicReference<>();
	private CountDownLatch received;

	@BeforeEach
	void startServer() throws IOException {
		received = new CountDownLatch(1);
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/v1/ping", exchange -> {
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
			received.countDown();
		});
		server.start();
		url = "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":"
			+ server.getAddress().getPort() + "/v1/ping";
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void sendsOnlyTheInstallIdAndWorldCount() throws Exception {
		UsagePing.sendAsync(url, "11111111-1111-4111-8111-111111111111", 3);

		assertTrue(received.await(10, TimeUnit.SECONDS), "the ping never arrived");
		String sent = body.get();

		// exactly two fields, form encoded, nothing else
		assertEquals(2, sent.split("&").length, "unexpected extra fields: " + sent);
		assertTrue(sent.contains("id=11111111-1111-4111-8111-111111111111"), sent);
		assertTrue(sent.contains("worlds=3"), sent);
	}

	@Test
	void clampsAnAbsurdCountRatherThanSendingIt() throws Exception {
		UsagePing.sendAsync(url, "11111111-1111-4111-8111-111111111111", Integer.MAX_VALUE);

		assertTrue(received.await(10, TimeUnit.SECONDS), "the ping never arrived");
		assertTrue(body.get().contains("worlds=1000000"), body.get());
	}

	@Test
	void aBlankEndpointSendsNothing() throws Exception {
		UsagePing.sendAsync("", "11111111-1111-4111-8111-111111111111", 3);
		UsagePing.sendAsync(null, "11111111-1111-4111-8111-111111111111", 3);

		assertFalse(received.await(1, TimeUnit.SECONDS), "a ping was sent with no endpoint");
	}

	@Test
	void anUnreachableEndpointIsSilent() {
		UsagePing.sendAsync("http://127.0.0.1:1/v1/ping", "11111111-1111-4111-8111-111111111111", 1);
	}
}
