package net.moonbowstudios.worldvault.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.moonbowstudios.worldvault.WorldVaultClient;
import net.moonbowstudios.worldvault.gui.CloudWorldsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen {

	protected SelectWorldScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void worldvault$addCloudButton(CallbackInfo ci) {
		addRenderableWidget(Button.builder(
				Component.translatable("worldvault.cloud.button"),
				button -> {
					if (this.minecraft != null) {
						this.minecraft.setScreenAndShow(new CloudWorldsScreen(this));
					}
				})
			.bounds(this.width - 104, 6, 98, 20)
			.build());

		worldvault$refreshBadges();
	}

	@Unique
	private void worldvault$refreshBadges() {
		WorldVaultClient mod = WorldVaultClient.get();
		if (mod == null || this.minecraft == null || mod.engine().provider().isEmpty()) {
			return;
		}

		Path savesDir = this.minecraft.getLevelSource().getBaseDir();
		Thread worker = new Thread(() -> {
			try (Stream<Path> entries = Files.list(savesDir)) {
				List<String> levelIds = entries
					.filter(Files::isDirectory)
					.map(path -> path.getFileName().toString())
					.toList();
				mod.engine().queueRefresh(levelIds);
			} catch (IOException e) {
				WorldVaultClient.LOGGER.warn("Could not list the saves directory", e);
			}
		}, "WorldVault-refresh");
		worker.setDaemon(true);
		worker.start();
	}
}
