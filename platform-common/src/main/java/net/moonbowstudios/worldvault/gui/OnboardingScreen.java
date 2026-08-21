package net.moonbowstudios.worldvault.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.moonbowstudios.worldvault.WorldVaultClient;

public final class OnboardingScreen extends Screen {

	private final Screen parent;

	public OnboardingScreen(Screen parent) {
		super(Component.translatable("worldvault.onboarding.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int centre = this.width / 2;
		int wide = 280;
		int y = 50;

		addRenderableWidget(new StringWidget(centre - wide / 2, y, wide, 12, this.title, this.font));
		y += 24;

		MultiLineTextWidget body = new MultiLineTextWidget(
			Component.translatable("worldvault.onboarding.body"), this.font).setMaxWidth(wide);
		body.setPosition(centre - wide / 2, y);
		addRenderableWidget(body);
		y += body.getHeight() + 30;

		addRenderableWidget(Button.builder(
			Component.translatable("worldvault.onboarding.link_now"), b -> linkNow())
			.bounds(centre - wide / 2, y, wide, 20).build());
		y += 24;

		addRenderableWidget(Button.builder(
			Component.translatable("worldvault.onboarding.later"), b -> onClose())
			.bounds(centre - wide / 2, y, wide, 20).build());
	}

	private void linkNow() {
		markSeen();
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(new WorldVaultSettingsScreen(parent));
		}
	}

	private void markSeen() {
		WorldVaultClient mod = WorldVaultClient.get();
		if (mod != null) {
			mod.config().hasSeenOnboarding = true;
			mod.saveConfig();
		}
	}

	@Override
	public void onClose() {
		markSeen();
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(parent);
		}
	}
}
