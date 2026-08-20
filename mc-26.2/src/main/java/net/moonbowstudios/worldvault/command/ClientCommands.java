package net.moonbowstudios.worldvault.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Builds command literals. Fabric renamed {@code ClientCommandManager} to {@code ClientCommands}
 * between the two game versions' command APIs, so this call is duplicated per version like
 * {@code ToastBridge}.
 */
public final class ClientCommands {

	private ClientCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
		return net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal(name);
	}
}
