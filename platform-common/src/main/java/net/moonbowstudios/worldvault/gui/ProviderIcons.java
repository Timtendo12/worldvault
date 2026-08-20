package net.moonbowstudios.worldvault.gui;

import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Sprite lookup for the cloud provider logos, mirroring {@link SyncIcons}. Draws via
 * {@code ImageWidget} rather than {@code blitSprite}, since the latter's host type was renamed
 * between game versions.
 */
public final class ProviderIcons {

	public static final int SIZE = 16;

	// keyed by CloudProvider.id(), which is also the sprite file name
	private static final Map<String, Identifier> LOGOS = Map.of(
		"dropbox", sprite("dropbox"),
		"googledrive", sprite("googledrive"));

	private ProviderIcons() {
	}

	private static Identifier sprite(String name) {
		return Identifier.fromNamespaceAndPath("worldvault", "provider/" + name);
	}

	/** @return the logo for this provider id, or null if there is no artwork for it. */
	public static Identifier forProvider(String providerId) {
		return providerId == null ? null : LOGOS.get(providerId);
	}

	/** Builds a positioned logo widget, or null when this provider has no artwork. */
	public static ImageWidget widgetFor(String providerId, int x, int y) {
		Identifier logo = forProvider(providerId);
		if (logo == null) {
			return null;
		}
		ImageWidget image = ImageWidget.sprite(SIZE, SIZE, logo);
		image.setX(x);
		image.setY(y);
		return image;
	}
}
