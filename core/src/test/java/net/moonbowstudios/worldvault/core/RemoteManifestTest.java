package net.moonbowstudios.worldvault.core;

import net.moonbowstudios.worldvault.core.cloud.RemoteManifest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteManifestTest {

	@Test
	void roundTripsEveryField() {
		RemoteManifest original = new RemoteManifest(
			42L, "8f3a", "Tim's laptop", 1755500000L, "New World", "26.2",
			Map.of("level.dat", "9f2b", "region/r.0.0.mca", "c14e"));

		RemoteManifest parsed = RemoteManifest.fromJson(original.toJson());

		assertEquals(42L, parsed.generation());
		assertEquals("8f3a", parsed.deviceId());
		assertEquals("Tim's laptop", parsed.deviceName());
		assertEquals(1755500000L, parsed.savedAt());
		assertEquals("New World", parsed.displayName());
		assertEquals("26.2", parsed.mcVersion());
		assertEquals(Map.of("level.dat", "9f2b", "region/r.0.0.mca", "c14e"), parsed.files());
	}

	@Test
	void carriesNoFieldWeDoNotUse() {
		String json = new String(new RemoteManifest(
			1L, "d", "D", 0L, "W", "26.2", Map.of()).toJson(), StandardCharsets.UTF_8);

		assertFalse(json.contains("levelId"));
		assertFalse(json.contains("modVersion"));
		assertTrue(json.contains("\"schema\""));
		assertTrue(json.contains("\"generation\""));
	}

	@Test
	void refusesAnUnknownSchemaRatherThanGuessing() {
		byte[] future = """
			{"schema":99,"generation":1,"deviceId":"x","files":{}}
			""".getBytes(StandardCharsets.UTF_8);

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
			() -> RemoteManifest.fromJson(future));
		assertTrue(e.getMessage().contains("schema 99"));
	}

	@Test
	void refusesGarbage() {
		assertThrows(IllegalArgumentException.class,
			() -> RemoteManifest.fromJson("not json at all".getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void toleratesAManifestWithNoFilesBlock() {
		byte[] json = "{\"schema\":1,\"generation\":1}".getBytes(StandardCharsets.UTF_8);
		assertTrue(RemoteManifest.fromJson(json).files().isEmpty());
	}
}
