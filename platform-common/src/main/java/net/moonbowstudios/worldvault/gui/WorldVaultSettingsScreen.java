package net.moonbowstudios.worldvault.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.moonbowstudios.worldvault.WorldVaultClient;
import net.moonbowstudios.worldvault.core.auth.OAuthFlow;
import net.moonbowstudios.worldvault.core.auth.ProviderConfig;
import net.moonbowstudios.worldvault.core.auth.TokenSet;
import net.moonbowstudios.worldvault.core.auth.TokenStore;
import net.moonbowstudios.worldvault.core.util.WorldVaultConfig;

import java.util.List;

public final class WorldVaultSettingsScreen extends Screen {

	private static final List<String> PROVIDERS = List.of("dropbox", "googledrive");

	private final Screen parent;
	private Component status = Component.empty();
	private volatile boolean closed;

	public WorldVaultSettingsScreen(Screen parent) {
		super(Component.translatable("worldvault.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		WorldVaultClient mod = WorldVaultClient.get();
		WorldVaultConfig config = mod.config();

		int centre = this.width / 2;
		int y = 32;
		int wide = 240;

		addRenderableWidget(new StringWidget(centre - wide / 2, y, wide, 12,
			this.title, this.font));
		y += 24;

		String active = config.activeProvider;
		for (String providerId : PROVIDERS) {
			ProviderConfig providerConfig = config.providerConfig(providerId);
			boolean linked = providerId.equals(active);
			boolean configured = providerConfig != null;

			Component label = Component.translatable(
				linked ? "worldvault.settings.unlink" : "worldvault.settings.link",
				providerConfig != null ? providerConfig.displayName() : providerId);

			Button button = Button.builder(label, b -> {
				if (linked) {
					unlink(providerId);
				} else {
					link(providerConfig);
				}
			}).bounds(centre - wide / 2, y, wide, 20).build();

			button.active = configured && (linked || mod.tokenStore().isAvailable());
			addRenderableWidget(button);
			ImageWidget logo = ProviderIcons.widgetFor(providerId,
				centre - wide / 2 - ProviderIcons.SIZE - 4, y + 2);
			if (logo != null) {
				addRenderableWidget(logo);
			}
			y += 24;
		}

		y += 6;

		addRenderableWidget(Button.builder(
			Component.translatable("worldvault.settings.on_close",
				onOff(config.syncOnWorldClose)), b -> {
				config.syncOnWorldClose = !config.syncOnWorldClose;
				WorldVaultClient.get().saveConfig();
				rebuild();
			}).bounds(centre - wide / 2, y, wide, 20).build());
		y += 24;

		addRenderableWidget(Button.builder(
			Component.translatable("worldvault.settings.while_playing",
				onOff(config.syncWhilePlaying)), b -> {
				config.syncWhilePlaying = !config.syncWhilePlaying;
				WorldVaultClient.get().saveConfig();
				rebuild();
			}).bounds(centre - wide / 2, y, wide, 20).build());
		y += 24;

		addRenderableWidget(Button.builder(
			Component.translatable("worldvault.settings.interval", config.intervalMinutes), b -> {
				config.intervalMinutes = switch (config.intervalMinutes) {
					case 5 -> 10;
					case 10 -> 15;
					case 15 -> 30;
					case 30 -> 60;
					default -> 5;
				};
				WorldVaultClient.get().saveConfig();
				rebuild();
			}).bounds(centre - wide / 2, y, wide, 20).build());
		y += 24;

		addRenderableWidget(Button.builder(
			Component.translatable("worldvault.settings.toasts",
				onOff(config.showToasts)), b -> {
				config.showToasts = !config.showToasts;
				WorldVaultClient.get().saveConfig();
				rebuild();
			}).bounds(centre - wide / 2, y, wide, 20).build());
		y += 8;

		addRenderableWidget(new StringWidget(centre - wide / 2, y, wide, 12, status, this.font));

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
			.bounds(centre - 100, this.height - 32, 200, 20).build());
	}

	private void rebuild() {
		this.rebuildWidgets();
	}



	private void link(ProviderConfig providerConfig) {
		if (providerConfig == null) {
			return;
		}
		status = Component.translatable("worldvault.settings.check_browser");
		rebuild();

		Thread worker = new Thread(() -> {
			try {
				TokenSet tokens = OAuthFlow.authorize(providerConfig);
				WorldVaultClient mod = WorldVaultClient.get();
				mod.tokenStore().save(providerConfig.id(), tokens);
				mod.config().activeProvider = providerConfig.id();
				mod.saveConfig();
				mod.reloadProvider();
				setStatusOnMainThread(Component.translatable("worldvault.settings.linked",
					providerConfig.displayName()));
			} catch (OAuthFlow.OAuthException | TokenStore.TokenStoreException e) {
				WorldVaultClient.LOGGER.warn("Linking {} failed", providerConfig.displayName(), e);
				setStatusOnMainThread(Component.literal(e.getMessage()));
			}
		}, "WorldVault-oauth");
		worker.setDaemon(true);
		worker.start();
	}

	private void unlink(String providerId) {
		WorldVaultClient mod = WorldVaultClient.get();
		try {
			mod.tokenStore().delete(providerId);
		} catch (TokenStore.TokenStoreException e) {
			WorldVaultClient.LOGGER.warn("Could not remove stored credentials", e);
		}
		mod.config().activeProvider = null;
		mod.saveConfig();
		mod.reloadProvider();
		status = Component.translatable("worldvault.settings.unlinked");
		rebuild();
	}

	private void setStatusOnMainThread(Component message) {
		if (this.minecraft == null) {
			return;
		}
		this.minecraft.execute(() -> {
			status = message;
			if (!closed) {
				rebuild();
			}
		});
	}

	private static Component onOff(boolean on) {
		return on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
	}

	@Override
	public void onClose() {
		closed = true;
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(parent);
		}
	}
}
