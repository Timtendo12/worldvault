package net.moonbowstudios.worldvault.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;

/**
 * Resolves the toast manager, the one part of the toast API that differs between versions:
 * 1.21.11 exposes it on {@code Minecraft}, 26.2 on {@code Gui}.
 */
public final class ToastBridge {

	private ToastBridge() {
	}

	/** @return the toast manager, or null before the client has finished starting up. */
	public static ToastManager manager() {
		Minecraft client = Minecraft.getInstance();
		return client == null || client.gui == null ? null : client.gui.toastManager();
	}
}
