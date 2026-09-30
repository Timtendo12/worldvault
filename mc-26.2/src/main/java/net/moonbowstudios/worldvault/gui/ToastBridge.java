package net.moonbowstudios.worldvault.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.moonbowstudios.worldvault.WorldVaultClient;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Resolves the toast manager, the one part of the toast API that differs between versions:
 * 1.21.11 and 26.1 expose it on {@code Minecraft}, 26.2+ on {@code Gui}.
 */
public final class ToastBridge {

	// looked up at runtime because this jar is compiled against 26.2 but also runs on 26.1
	private static final MethodHandle FROM_GUI = find(Gui.class, "toastManager");
	private static final MethodHandle FROM_CLIENT = FROM_GUI != null ? null : find(Minecraft.class, "getToastManager");

	static {
		if (FROM_GUI == null && FROM_CLIENT == null) {
			WorldVaultClient.LOGGER.warn("WorldVault could not find the toast manager; sync toasts are off.");
		}
	}

	private ToastBridge() {
	}

	/** @return the toast manager, or null before the client has finished starting up. */
	public static ToastManager manager() {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return null;
		}
		try {
			if (FROM_GUI != null) {
				return client.gui == null ? null : (ToastManager) FROM_GUI.invoke(client.gui);
			}
			if (FROM_CLIENT != null) {
				return (ToastManager) FROM_CLIENT.invoke(client);
			}
		} catch (Throwable e) {
			WorldVaultClient.LOGGER.warn("WorldVault could not reach the toast manager.", e);
		}
		return null;
	}

	private static MethodHandle find(Class<?> owner, String name) {
		try {
			return MethodHandles.publicLookup().findVirtual(owner, name, MethodType.methodType(ToastManager.class));
		} catch (NoSuchMethodException | IllegalAccessException e) {
			return null;
		}
	}
}
