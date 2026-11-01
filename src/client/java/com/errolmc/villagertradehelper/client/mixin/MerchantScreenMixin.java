package com.errolmc.villagertradehelper.client.mixin;

import com.errolmc.villagertradehelper.client.gui.TradeOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the stock bars and the Shift tooltip over the offer list. Here rather than in Fabric's after-extract screen
 * event because that runs after the screen's tooltips have been drawn.
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin {
	@Inject(method = "extractContents", at = @At("TAIL"))
	private void villagerTradeHelper$overlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		TradeOverlay.extract((MerchantScreen) (Object) this, graphics, mouseX, mouseY);
	}
}
