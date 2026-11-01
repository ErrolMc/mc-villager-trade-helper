package com.errolmc.villagertradehelper.client.gui;

import com.errolmc.villagertradehelper.client.config.TradeHelperConfig;
import com.errolmc.villagertradehelper.client.memory.TradeMemory;
import com.errolmc.villagertradehelper.client.mixin.AbstractContainerScreenAccessor;
import com.errolmc.villagertradehelper.client.mixin.MerchantScreenAccessor;
import com.errolmc.villagertradehelper.client.trade.Offers;
import com.errolmc.villagertradehelper.client.trade.TradeRunner;
import com.errolmc.villagertradehelper.trade.TradePlanner.Estimate;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;

/** The Trade All button, shift-click on an offer, and cleanup when the trade screen closes. */
public final class TradeScreenHooks {
	/** The free strip under the payment and result slots, above the "Inventory" label row. */
	private static final int BUTTON_X = 136;
	private static final int BUTTON_Y = 58;
	private static final int BUTTON_WIDTH = 102;

	private TradeScreenHooks() {
	}

	public static void attach(MerchantScreen screen) {
		MerchantMenu menu = screen.getMenu();
		AbstractContainerScreenAccessor panel = (AbstractContainerScreenAccessor) screen;
		List<TradeButton> buttons = new ArrayList<>();

		if (TradeHelperConfig.get().showButtons) {
			buttons.add(new TradeButton(
				() -> panel.villagerTradeHelper$leftPos() + BUTTON_X,
				() -> panel.villagerTradeHelper$topPos() + BUTTON_Y,
				BUTTON_WIDTH,
				() -> TradeRunner.isRunning(menu)
					? Component.translatable("villager-trade-helper.button.stop")
					: Component.translatable("villager-trade-helper.button.trade_all"),
				() -> tradeAllTooltip(screen),
				() -> TradeRunner.isRunning(menu) || selectedEstimate(screen).trades() > 0,
				() -> {
					if (TradeRunner.isRunning(menu)) {
						TradeRunner.stop();
					} else {
						TradeRunner.tradeAll(screen, selected(screen));
					}
				}));
			Screens.getWidgets(screen).addAll(buttons);
		}

		ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> allowClick(screen, buttons, event));
		ScreenEvents.remove(screen).register(current -> {
			TradeRunner.onScreenClosed(menu);
			TradeMemory.onTradeScreenClosed(screen);
		});
	}

	private static boolean allowClick(MerchantScreen screen, List<TradeButton> buttons, MouseButtonEvent event) {
		MerchantMenu menu = screen.getMenu();
		if (TradeRunner.isRunning(menu)) {
			// Clicks in the inventory now would mix with the running trades; only the Stop button works.
			return buttons.stream().anyMatch(button -> button.isMouseOver(event.x(), event.y()));
		}
		// The keyboard state, like the overlay's Shift tooltip, so the click does what the tooltip said.
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && Minecraft.getInstance().hasShiftDown() && TradeHelperConfig.get().shiftClickTradesAll) {
			int offer = TradeOverlay.offerAt(screen, event.x(), event.y());
			if (offer >= 0) {
				TradeRunner.tradeAll(screen, offer);
				return false;
			}
		}
		return true;
	}

	static int selected(MerchantScreen screen) {
		return ((MerchantScreenAccessor) screen).villagerTradeHelper$shopItem();
	}

	private static Estimate selectedEstimate(MerchantScreen screen) {
		MerchantMenu menu = screen.getMenu();
		int index = selected(screen);
		MerchantOffers offers = menu.getOffers();
		if (index < 0 || index >= offers.size()) {
			return new Estimate(0, null);
		}
		return Offers.estimate(menu, offers.get(index));
	}

	private static Component tradeAllTooltip(MerchantScreen screen) {
		MerchantMenu menu = screen.getMenu();
		if (TradeRunner.isRunning(menu)) {
			return Component.translatable("villager-trade-helper.button.stop.tooltip");
		}
		MutableComponent text = Component.translatable("villager-trade-helper.button.trade_all.tooltip");
		int index = selected(screen);
		MerchantOffers offers = menu.getOffers();
		if (index >= 0 && index < offers.size()) {
			text.append("\n\n").append(TradeOverlay.preview(menu, offers.get(index)));
		}
		if (TradeHelperConfig.get().shiftClickTradesAll) {
			text.append("\n\n").append(Component.translatable("villager-trade-helper.button.trade_all.shift_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
		return text;
	}
}
