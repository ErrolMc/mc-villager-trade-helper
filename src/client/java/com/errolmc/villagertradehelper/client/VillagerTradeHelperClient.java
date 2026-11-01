package com.errolmc.villagertradehelper.client;

import com.errolmc.villagertradehelper.client.config.TradeHelperConfig;
import com.errolmc.villagertradehelper.client.gui.TradeScreenHooks;
import com.errolmc.villagertradehelper.client.memory.TradeHud;
import com.errolmc.villagertradehelper.client.memory.TradeMemory;
import com.errolmc.villagertradehelper.client.trade.TradeRunner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.resources.Identifier;

public class VillagerTradeHelperClient implements ClientModInitializer {
	public static final String MOD_ID = "villager-trade-helper";

	@Override
	public void onInitializeClient() {
		TradeHelperConfig.load();
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof MerchantScreen merchantScreen) {
				TradeMemory.onTradeScreenOpened(merchantScreen);
				TradeScreenHooks.attach(merchantScreen);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			TradeRunner.tick(minecraft);
			TradeMemory.tick(minecraft);
		});
		UseEntityCallback.EVENT.register(TradeMemory::onInteract);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Identifier.fromNamespaceAndPath(MOD_ID, "trades"), TradeHud::extract);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> TradeMemory.reset());
	}
}
