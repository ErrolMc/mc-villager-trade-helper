package com.errolmc.villagertradehelper.client.gui;

import com.errolmc.villagertradehelper.client.config.TradeHelperConfig;
import com.errolmc.villagertradehelper.client.mixin.AbstractContainerScreenAccessor;
import com.errolmc.villagertradehelper.client.mixin.MerchantScreenAccessor;
import com.errolmc.villagertradehelper.client.trade.Offers;
import com.errolmc.villagertradehelper.client.trade.TradeRunner;
import com.errolmc.villagertradehelper.trade.TradePlanner.Estimate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Drawn on top of the trade screen (from {@code MerchantScreenMixin}, after vanilla has queued its tooltips): stock
 * bars and dimming on the offer list, the Shift tooltip with what Trade All would do, and the status line.
 */
public final class TradeOverlay {
	/** Offer list geometry in {@code MerchantScreen}: 7 buttons of 88x20 from (5, 18). */
	private static final int LIST_X = 5;
	private static final int LIST_Y = 18;
	private static final int ROW_WIDTH = 88;
	private static final int ROW_HEIGHT = 20;
	private static final int VISIBLE_ROWS = 7;
	private static final int IMAGE_WIDTH = 276;

	private static final int DIM = 0x88000000;
	private static final int BAR_TRACK = 0xFF2B2B2B;
	private static final int HOVER_OUTLINE = 0xFFFFD040;
	private static final int RUNNING_OUTLINE = 0xFF60E060;
	private static final int STATUS_WIDTH = 320;

	private TradeOverlay() {
	}

	/** The offer index under the mouse in the offer list, or -1. */
	public static int offerAt(MerchantScreen screen, double mouseX, double mouseY) {
		MerchantOffers offers = screen.getMenu().getOffers();
		AbstractContainerScreenAccessor panel = (AbstractContainerScreenAccessor) screen;
		int x = panel.villagerTradeHelper$leftPos() + LIST_X;
		int y = panel.villagerTradeHelper$topPos() + LIST_Y;
		if (mouseX < x || mouseX >= x + ROW_WIDTH || mouseY < y) {
			return -1;
		}
		int row = (int) (mouseY - y) / ROW_HEIGHT;
		int index = row + scrollOff(screen);
		return row < VISIBLE_ROWS && index < offers.size() ? index : -1;
	}

	private static int scrollOff(MerchantScreen screen) {
		// Vanilla only scrolls lists longer than the 7 buttons, but keep a stale value from turning into a bad index.
		return screen.getMenu().getOffers().size() > VISIBLE_ROWS ? ((MerchantScreenAccessor) screen).villagerTradeHelper$scrollOff() : 0;
	}

	public static void extract(MerchantScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Minecraft minecraft = Minecraft.getInstance();
		TradeHelperConfig config = TradeHelperConfig.get();
		MerchantMenu menu = screen.getMenu();
		MerchantOffers offers = menu.getOffers();
		AbstractContainerScreenAccessor panel = (AbstractContainerScreenAccessor) screen;
		int left = panel.villagerTradeHelper$leftPos();
		int top = panel.villagerTradeHelper$topPos();

		// Above the offer items vanilla has drawn.
		graphics.nextStratum();

		int scrollOff = scrollOff(screen);
		int hovered = offerAt(screen, mouseX, mouseY);
		boolean running = TradeRunner.isRunning(menu);
		for (int row = 0; row < VISIBLE_ROWS && row + scrollOff < offers.size(); row++) {
			int index = row + scrollOff;
			MerchantOffer offer = offers.get(index);
			int x = left + LIST_X;
			int y = top + LIST_Y + row * ROW_HEIGHT;
			if (config.dimUnaffordable && !offer.isOutOfStock() && Offers.estimate(menu, offer).trades() == 0) {
				graphics.fill(x + 1, y + 1, x + ROW_WIDTH - 1, y + ROW_HEIGHT - 1, DIM);
			}
			if (config.showStockBars && offer.getMaxUses() > 0) {
				drawStockBar(graphics, x + 1, y + ROW_HEIGHT - 2, ROW_WIDTH - 2, Offers.stockLeft(offer), offer.getMaxUses());
			}
			if (running && index == TradeScreenHooks.selected(screen)) {
				graphics.outline(x, y, ROW_WIDTH, ROW_HEIGHT, RUNNING_OUTLINE);
			} else if (!running && index == hovered && minecraft.hasShiftDown() && config.shiftClickTradesAll) {
				graphics.outline(x, y, ROW_WIDTH, ROW_HEIGHT, HOVER_OUTLINE);
			}
		}

		if (hovered >= 0 && !running && minecraft.hasShiftDown() && config.shiftClickTradesAll) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable("villager-trade-helper.tooltip.shift_click").withStyle(ChatFormatting.YELLOW));
			lines.addAll(previewLines(menu, offers.get(hovered)));
			List<FormattedCharSequence> wrapped = new ArrayList<>();
			for (Component line : lines) {
				wrapped.addAll(minecraft.font.split(line, 240));
			}
			// Replaces the item tooltip vanilla queued for the hovered row.
			graphics.setTooltipForNextFrame(minecraft.font, wrapped, DefaultTooltipPositioner.INSTANCE, mouseX, mouseY, true);
		}

		Component status = TradeRunner.status(menu);
		if (status != null) {
			drawStatus(graphics, minecraft.font, status, left + IMAGE_WIDTH / 2, top);
		}
	}

	private static void drawStockBar(GuiGraphicsExtractor graphics, int x, int y, int width, int left, int max) {
		float fraction = Mth.clamp(left / (float) max, 0, 1);
		graphics.fill(x, y, x + width, y + 1, BAR_TRACK);
		int filled = Math.round(width * fraction);
		if (filled > 0) {
			// Red when empty through yellow to green when full, like the item durability bar.
			int color = 0xFF000000 | Mth.hsvToRgb(fraction / 3.0F, 1.0F, 1.0F);
			graphics.fill(x, y, x + filled, y + 1, color);
		}
	}

	private static void drawStatus(GuiGraphicsExtractor graphics, Font font, Component status, int centerX, int top) {
		List<FormattedCharSequence> lines = font.split(status, STATUS_WIDTH);
		int y = Math.max(2, top - 3 - lines.size() * font.lineHeight);
		for (FormattedCharSequence line : lines) {
			graphics.centeredText(font, line, centerX, y, 0xFFFFFFFF);
			y += font.lineHeight;
		}
	}

	/** What Trade All on {@code offer} would do, one line per entry. */
	public static List<Component> previewLines(MerchantMenu menu, MerchantOffer offer) {
		List<Component> lines = new ArrayList<>();
		Estimate estimate = Offers.estimate(menu, offer);
		if (estimate.trades() == 0) {
			lines.add(Component.translatable("villager-trade-helper.message.cannot_trade", TradeRunner.reasonText(menu, offer, estimate.limit()))
				.withStyle(ChatFormatting.RED));
		} else {
			lines.add(Component.translatable("villager-trade-helper.tooltip.would_trade", estimate.trades(), Offers.describe(offer, estimate.trades()))
				.withStyle(ChatFormatting.WHITE));
			lines.add(Component.translatable("villager-trade-helper.tooltip.then", TradeRunner.reasonText(menu, offer, estimate.limit()))
				.withStyle(ChatFormatting.GRAY));
			int fit = Offers.roomFor(menu, offer.getResult()) / Math.max(1, offer.getResult().getCount());
			if (fit < estimate.trades()) {
				lines.add(Component.translatable("villager-trade-helper.tooltip.may_fill_up").withStyle(ChatFormatting.GOLD));
			}
		}
		lines.add(Component.translatable("villager-trade-helper.tooltip.stock", Offers.stockLeft(offer), offer.getMaxUses())
			.withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	/** {@link #previewLines} as one text with line breaks, for widget tooltips. */
	public static Component preview(MerchantMenu menu, MerchantOffer offer) {
		MutableComponent text = Component.empty();
		List<Component> lines = previewLines(menu, offer);
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				text.append("\n");
			}
			text.append(lines.get(i));
		}
		return text;
	}
}
