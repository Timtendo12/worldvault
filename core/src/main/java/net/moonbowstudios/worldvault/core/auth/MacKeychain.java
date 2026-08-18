package net.moonbowstudios.worldvault.core.auth;

import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import net.moonbowstudios.worldvault.core.auth.KeychainNatives.CFTypeRef;
import net.moonbowstudios.worldvault.core.auth.KeychainNatives.CoreFoundation;
import net.moonbowstudios.worldvault.core.auth.KeychainNatives.Security;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class MacKeychain {

	private static final CoreFoundation CF = CoreFoundation.INSTANCE;
	private static final Security SEC = Security.INSTANCE;

	private static final NativeLibrary SECURITY_LIB = NativeLibrary.getInstance("Security");
	private static final CFTypeRef K_SEC_CLASS = global("kSecClass");
	private static final CFTypeRef K_SEC_CLASS_GENERIC_PASSWORD = global("kSecClassGenericPassword");
	private static final CFTypeRef K_SEC_ATTR_SERVICE = global("kSecAttrService");
	private static final CFTypeRef K_SEC_ATTR_ACCOUNT = global("kSecAttrAccount");
	private static final CFTypeRef K_SEC_VALUE_DATA = global("kSecValueData");
	private static final CFTypeRef K_SEC_RETURN_DATA = global("kSecReturnData");
	private static final CFTypeRef K_SEC_MATCH_LIMIT = global("kSecMatchLimit");
	private static final CFTypeRef K_SEC_MATCH_LIMIT_ONE = global("kSecMatchLimitOne");
	private static final NativeLibrary CF_LIB = NativeLibrary.getInstance("CoreFoundation");
	private static final CFTypeRef K_CF_BOOLEAN_TRUE = coreFoundationGlobal("kCFBooleanTrue");

	// struct addresses, not null, or the dictionary will not retain its keys and values
	private static final Pointer CF_TYPE_DICT_KEY_CALLBACKS =
		CF_LIB.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks");
	private static final Pointer CF_TYPE_DICT_VALUE_CALLBACKS =
		CF_LIB.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks");

	private MacKeychain() {
	}

	private static CFTypeRef global(String name) {
		return new CFTypeRef(SECURITY_LIB.getGlobalVariableAddress(name).getPointer(0));
	}

	private static CFTypeRef coreFoundationGlobal(String name) {
		return new CFTypeRef(CF_LIB.getGlobalVariableAddress(name).getPointer(0));
	}

	static boolean isAvailable() {
		try {
			return K_SEC_CLASS != null && K_SEC_CLASS.getPointer() != null;
		} catch (Throwable t) {
			return false;
		}
	}

	static void store(String service, String account, byte[] secret) {
		Scope scope = new Scope();
		try {
			CFTypeRef query = scope.dictionary();
			CF.CFDictionarySetValue(query, K_SEC_CLASS, K_SEC_CLASS_GENERIC_PASSWORD);
			CF.CFDictionarySetValue(query, K_SEC_ATTR_SERVICE, scope.string(service));
			CF.CFDictionarySetValue(query, K_SEC_ATTR_ACCOUNT, scope.string(account));

			CFTypeRef payload = scope.dictionary();
			CF.CFDictionarySetValue(payload, K_SEC_VALUE_DATA, scope.data(secret));

			int status = SEC.SecItemUpdate(query, payload);
			if (status == Security.errSecItemNotFound) {
				CF.CFDictionarySetValue(query, K_SEC_VALUE_DATA, scope.data(secret));
				status = SEC.SecItemAdd(query, null);
			}
			if (status != Security.errSecSuccess) {
				throw new IllegalStateException("Keychain write failed, OSStatus " + status);
			}
		} finally {
			scope.close();
		}
	}

	static byte[] load(String service, String account) {
		Scope scope = new Scope();
		try {
			CFTypeRef query = scope.dictionary();
			CF.CFDictionarySetValue(query, K_SEC_CLASS, K_SEC_CLASS_GENERIC_PASSWORD);
			CF.CFDictionarySetValue(query, K_SEC_ATTR_SERVICE, scope.string(service));
			CF.CFDictionarySetValue(query, K_SEC_ATTR_ACCOUNT, scope.string(account));
			CF.CFDictionarySetValue(query, K_SEC_RETURN_DATA, K_CF_BOOLEAN_TRUE);
			CF.CFDictionarySetValue(query, K_SEC_MATCH_LIMIT, K_SEC_MATCH_LIMIT_ONE);

			CFTypeRef[] out = new CFTypeRef[1];
			int status = SEC.SecItemCopyMatching(query, out);
			if (status == Security.errSecItemNotFound) {
				return null;
			}
			if (status != Security.errSecSuccess) {
				throw new IllegalStateException("Keychain read failed, OSStatus " + status);
			}

			CFTypeRef data = out[0];
			try {
				int length = (int) CF.CFDataGetLength(data);
				Pointer bytes = CF.CFDataGetBytePtr(data);
				return bytes.getByteArray(0, length);
			} finally {
				CF.CFRelease(data);
			}
		} finally {
			scope.close();
		}
	}

	static void delete(String service, String account) {
		Scope scope = new Scope();
		try {
			CFTypeRef query = scope.dictionary();
			CF.CFDictionarySetValue(query, K_SEC_CLASS, K_SEC_CLASS_GENERIC_PASSWORD);
			CF.CFDictionarySetValue(query, K_SEC_ATTR_SERVICE, scope.string(service));
			CF.CFDictionarySetValue(query, K_SEC_ATTR_ACCOUNT, scope.string(account));

			int status = SEC.SecItemDelete(query);
			if (status != Security.errSecSuccess && status != Security.errSecItemNotFound) {
				throw new IllegalStateException("Keychain delete failed, OSStatus " + status);
			}
		} finally {
			scope.close();
		}
	}

	private static final class Scope implements AutoCloseable {
		private final List<CFTypeRef> owned = new ArrayList<>();

		CFTypeRef dictionary() {
			return track(CF.CFDictionaryCreateMutable(null, 0,
				CF_TYPE_DICT_KEY_CALLBACKS, CF_TYPE_DICT_VALUE_CALLBACKS));
		}

		CFTypeRef string(String value) {
			byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
			return track(CF.CFStringCreateWithBytes(null, utf8, utf8.length,
				CoreFoundation.kCFStringEncodingUTF8, false));
		}

		CFTypeRef data(byte[] value) {
			return track(CF.CFDataCreate(null, value, value.length));
		}

		private CFTypeRef track(CFTypeRef ref) {
			owned.add(ref);
			return ref;
		}

		@Override
		public void close() {
			for (CFTypeRef ref : owned) {
				if (ref != null && ref.getPointer() != null) {
					CF.CFRelease(ref);
				}
			}
			owned.clear();
		}
	}
}
