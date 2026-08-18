package net.moonbowstudios.worldvault.core.auth;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;

public final class KeychainNatives {

	private KeychainNatives() {
	}

	public static class CFTypeRef extends PointerType {
		public CFTypeRef() {
		}

		public CFTypeRef(Pointer p) {
			super(p);
		}
	}

	public interface CoreFoundation extends Library {
		CoreFoundation INSTANCE = Native.load("CoreFoundation", CoreFoundation.class);

		int kCFStringEncodingUTF8 = 0x08000100;

		CFTypeRef CFStringCreateWithBytes(Pointer alloc, byte[] bytes, long numBytes,
		                                  int encoding, boolean isExternalRepresentation);

		CFTypeRef CFDataCreate(Pointer alloc, byte[] bytes, long length);

		CFTypeRef CFDictionaryCreateMutable(Pointer alloc, long capacity,
		                                    Pointer keyCallBacks, Pointer valueCallBacks);

		void CFDictionarySetValue(CFTypeRef dict, CFTypeRef key, CFTypeRef value);

		long CFDataGetLength(CFTypeRef data);

		Pointer CFDataGetBytePtr(CFTypeRef data);

		void CFRelease(CFTypeRef ref);
	}

	public interface Security extends Library {
		Security INSTANCE = Native.load("Security", Security.class);

		int errSecSuccess = 0;
		int errSecItemNotFound = -25300;
		int errSecDuplicateItem = -25299;

		int SecItemAdd(CFTypeRef attributes, Pointer result);

		int SecItemCopyMatching(CFTypeRef query, CFTypeRef[] result);

		int SecItemUpdate(CFTypeRef query, CFTypeRef attributesToUpdate);

		int SecItemDelete(CFTypeRef query);
	}

	public interface Secret extends Library {
		Secret INSTANCE = Native.load("secret-1", Secret.class);

		boolean secret_password_store_sync(Pointer schema, String collection, String label,
		                                   String password, Pointer cancellable,
		                                   Pointer error, String... attributes);

		Pointer secret_password_lookup_sync(Pointer schema, Pointer cancellable, Pointer error,
		                                    String... attributes);

		boolean secret_password_clear_sync(Pointer schema, Pointer cancellable, Pointer error,
		                                   String... attributes);

		void secret_password_free(Pointer password);
	}
}
