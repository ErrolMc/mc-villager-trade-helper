package com.errolmc.villagertradehelper.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the panel position, which the buttons and overlays are anchored to. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int villagerTradeHelper$leftPos();

	@Accessor("topPos")
	int villagerTradeHelper$topPos();
}
