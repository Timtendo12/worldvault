package net.moonbowstudios.worldvault.core.cloud;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPathTest {

	@Test
	void dropboxEscapesNonAsciiBecauseItsArgumentsRideInAnHttpHeader() {
		JsonObject arg = new JsonObject();
		arg.addProperty("path", "/Mijn Wereld ☃/manifest.json");

		String header = DropboxProvider.jsonHeader(arg);

		assertTrue(header.chars().allMatch(c -> c >= 0x20 && c <= 0x7E),
			"header must be printable ASCII, got: " + header);
		assertTrue(header.contains("\\u2603"), "the snowman must survive as an escape: " + header);
	}

	@Test
	void dropboxLeavesPlainAsciiAlone() {
		JsonObject arg = new JsonObject();
		arg.addProperty("path", "/New World/manifest.json");

		assertEquals("{\"path\":\"/New World/manifest.json\"}", DropboxProvider.jsonHeader(arg));
	}

	@Test
	void oneDriveEncodesEachSegmentButKeepsSlashesStructural() {
		String url = OneDriveProvider.itemUrl("New World/world/region/r.0.0.mca");

		assertTrue(url.endsWith(":/New%20World/world/region/r.0.0.mca"), url);
		assertFalse(url.contains("+"), "a space must be %20, not +, inside a path");
	}

	@Test
	void oneDriveEncodesCharactersThatWouldOtherwiseBreakTheUrl() {
		String url = OneDriveProvider.itemUrl("My World? #1/manifest.json");

		assertFalse(url.contains("?"), "an unencoded ? would start the query string: " + url);
		assertFalse(url.contains("#"), "an unencoded # would start a fragment: " + url);
	}

	@Test
	void googleDriveEscapesQuotesInTheSearchQuery() {
		assertEquals("Tim\\'s World", GoogleDriveProvider.escapeQuery("Tim's World"));
		assertEquals("back\\\\slash", GoogleDriveProvider.escapeQuery("back\\slash"));
	}

	@Test
	void googleDriveSplitsPathsIntoParentAndName() {
		assertEquals("New World/world/region", GoogleDriveProvider.parentOf("New World/world/region/r.0.0.mca"));
		assertEquals("r.0.0.mca", GoogleDriveProvider.nameOf("New World/world/region/r.0.0.mca"));

		assertEquals("", GoogleDriveProvider.parentOf("manifest.json"));
		assertEquals("manifest.json", GoogleDriveProvider.nameOf("manifest.json"));
	}

	@Test
	void googleDriveMultipartBodyKeepsThePayloadByteExact() {
		byte[] payload = {0x00, (byte) 0xFF, 0x7F, (byte) 0x80};
		byte[] body = GoogleDriveProvider.multipartBody("BOUND", "{\"name\":\"x\"}", payload);
		String text = new String(body, StandardCharsets.ISO_8859_1);

		assertTrue(text.startsWith("--BOUND\r\nContent-Type: application/json"));
		assertTrue(text.endsWith("\r\n--BOUND--"));
		int start = text.indexOf("octet-stream\r\n\r\n") + "octet-stream\r\n\r\n".length();
		byte[] roundTripped = java.util.Arrays.copyOfRange(body, start, start + payload.length);
		assertArrayEquals(payload, roundTripped);
	}

	private static void assertArrayEquals(byte[] expected, byte[] actual) {
		org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
	}
}
