package net.moonbowstudios.worldvault.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.moonbowstudios.worldvault.core.sync.SyncStatusRegistry;
import net.moonbowstudios.worldvault.core.sync.WorldSyncStatus;

public final class SyncBadge {

	private static final int MARGIN = 4;

	private SyncBadge() {
	}

	public static void render(WorldSelectionList.Entry entry, GuiGraphics graphics,
	                          int mouseX, int mouseY, String levelId) {
		WorldSyncStatus status = SyncStatusRegistry.get(levelId);
		if (!status.state().isVisible()) {
			return;
		}

		Identifier icon = SyncIcons.iconFor(status, monotonicMillis());
		if (icon == null) {
			return;
		}

		int x = entry.getContentRight() - SyncIcons.SIZE - MARGIN;
		int y = entry.getContentY() + (entry.getContentHeight() - SyncIcons.SIZE) / 2;

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x, y, SyncIcons.SIZE, SyncIcons.SIZE);

		if (isOver(mouseX, mouseY, x, y)) {
			Component tooltip = SyncIcons.tooltipFor(status);
			if (tooltip != null) {
				graphics.setTooltipForNextFrame(tooltip, mouseX, mouseY);
			}
		}
	}

	private static long monotonicMillis() {
		return System.nanoTime() / 1_000_000L;
	}

	private static boolean isOver(int mouseX, int mouseY, int x, int y) {
		return mouseX >= x && mouseX < x + SyncIcons.SIZE
			&& mouseY >= y && mouseY < y + SyncIcons.SIZE;
	}
}
