package net.moonbowstudios.worldvault.core.auth;

import com.sun.jna.Pointer;
import net.moonbowstudios.worldvault.core.auth.KeychainNatives.Secret;

import java.nio.charset.StandardCharsets;

final class LinuxSecretStore {

	private static final String COLLECTION = "default";

	private static final boolean AVAILABLE = probe();

	private LinuxSecretStore() {
	}

	private static boolean probe() {
		try {
			return Secret.INSTANCE != null;
		} catch (Throwable t) {
			return false;
		}
	}

	static boolean isAvailable() {
		return AVAILABLE;
	}

	static void store(String service, String account, byte[] secret) {
		boolean ok = Secret.INSTANCE.secret_password_store_sync(
			null, COLLECTION, service + " (" + account + ")",
			new String(secret, StandardCharsets.UTF_8), null, null,
			"service", service, "account", account, null);

		if (!ok) {
			throw new IllegalStateException("libsecret refused to store the credential");
		}
	}

	static byte[] load(String service, String account) {
		Pointer result = Secret.INSTANCE.secret_password_lookup_sync(
			null, null, null, "service", service, "account", account, null);

		if (result == null) {
			return null;
		}
		try {
			return result.getString(0, "UTF-8").getBytes(StandardCharsets.UTF_8);
		} finally {
			Secret.INSTANCE.secret_password_free(result);
		}
	}

	static void delete(String service, String account) {
		Secret.INSTANCE.secret_password_clear_sync(
			null, null, null, "service", service, "account", account, null);
	}
}
