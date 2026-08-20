package net.moonbowstudios.worldvault.command;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.moonbowstudios.worldvault.WorldVaultClient;
import net.moonbowstudios.worldvault.platform.SnapshotService;

/**
 * Registers {@code /worldvault sync}, a client-side command that uploads the world you are
 * currently playing without waiting for the automatic cycle.
 */
public final class WorldVaultCommands {

	private WorldVaultCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
			dispatcher.register(ClientCommands.literal("worldvault")
				.then(ClientCommands.literal("sync")
					.executes(ctx -> syncNow(ctx.getSource())))));
	}

	private static int syncNow(FabricClientCommandSource source) {
		WorldVaultClient mod = WorldVaultClient.get();
		if (mod == null) {
			source.sendError(Component.translatable("worldvault.command.unavailable"));
			return 0;
		}

		// a client command is available on servers and on other people's worlds too, where there
		// is nothing of ours to upload
		if (Minecraft.getInstance().getSingleplayerServer() == null) {
			source.sendError(Component.translatable("worldvault.command.singleplayer_only"));
			return 0;
		}

		if (mod.engine().provider().isEmpty()) {
			source.sendError(Component.translatable("worldvault.command.not_linked"));
			return 0;
		}

		if (!SnapshotService.syncNow()) {
			source.sendError(Component.translatable("worldvault.command.no_world"));
			return 0;
		}

		String world = SnapshotService.openDisplayName();
		source.sendFeedback(Component.translatable("worldvault.command.syncing",
			world != null ? world : SnapshotService.openLevelId()));
		return 1;
	}
}
