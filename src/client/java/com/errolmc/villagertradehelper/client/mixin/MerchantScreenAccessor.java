package com.errolmc.villagertradehelper.client.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The selected offer and the scroll position of the offer list, and the method an offer button calls when clicked. */
@Mixin(MerchantScreen.class)
public interface MerchantScreenAccessor {
	@Accessor("shopItem")
	int villagerTradeHelper$shopItem();

	@Accessor("shopItem")
	void villagerTradeHelper$setShopItem(int shopItem);

	@Accessor("scrollOff")
	int villagerTradeHelper$scrollOff();

	/** Selects {@code shopItem} the way clicking its button does: moves the payment in and tells the server. */
	@Invoker("postButtonClick")
	void villagerTradeHelper$postButtonClick();
}
