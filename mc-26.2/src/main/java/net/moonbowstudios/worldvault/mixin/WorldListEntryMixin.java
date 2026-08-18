package net.moonbowstudios.worldvault.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import net.moonbowstudios.worldvault.gui.SyncBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class WorldListEntryMixin {

	@Shadow
	public abstract LevelSummary getLevelSummary();

	@Inject(method = "extractContent", at = @At("TAIL"))
	private void worldvault$renderSyncBadge(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
	                                        boolean hovered, float partialTick, CallbackInfo ci) {
		SyncBadge.render((WorldSelectionList.Entry) (Object) this, extractor, mouseX, mouseY,
			getLevelSummary().getLevelId());
	}
}
