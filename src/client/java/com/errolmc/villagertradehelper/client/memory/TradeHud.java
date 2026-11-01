package com.errolmc.villagertradehelper.client.memory;

import com.errolmc.villagertradehelper.client.config.HudMode;
import com.errolmc.villagertradehelper.client.config.TradeHelperConfig;
import com.errolmc.villagertradehelper.client.trade.Offers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/** Next to the crosshair: the offers of the villager you're looking at, as they were when you last traded with it. */
public final class TradeHud {
	private static final int GAP = 10;
	private static final int PADDING = 3;
	private static final int BACKGROUND = 0xA0000000;

	private TradeHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		HudMode mode = TradeHelperConfig.get().hudMode;
		if (mode == HudMode.OFF || minecraft.gui.screen() != null || minecraft.player == null
			|| (mode == HudMode.SNEAKING && !minecraft.player.isShiftKeyDown())
			|| !(minecraft.crosshairPickEntity instanceof AbstractVillager villager)) {
			return;
		}
		TradeMemory.Remembered remembered = TradeMemory.get(villager);
		MerchantOffers offers = remembered != null ? remembered.offers() : null;
		if (offers == null || offers.isEmpty()) {
			return;
		}

		List<Component> lines = new ArrayList<>();
		lines.add(title(remembered));
		for (MerchantOffer offer : offers) {
			lines.add(line(offer));
		}

		Font font = minecraft.font;
		int width = 0;
		for (Component line : lines) {
			width = Math.max(width, font.width(line));
		}
		int height = lines.size() * font.lineHeight;
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();
		// Right of the crosshair if it fits, else left of it, else below it, never over it.
		int x = screenWidth / 2 + GAP;
		int y = screenHeight / 2 - height / 2;
		if (x + width + PADDING > screenWidth) {
			x = screenWidth / 2 - GAP - width;
			if (x - PADDING < 0) {
				x = Math.max(PADDING, Math.min(screenWidth / 2 - width / 2, screenWidth - width - PADDING));
				y = screenHeight / 2 + GAP;
			}
		}
		y = Math.max(PADDING, Math.min(y, screenHeight - height - PADDING));

		graphics.fill(x - PADDING, y - PADDING, x + width + PADDING, y + height + PADDING - 1, BACKGROUND);
		for (Component line : lines) {
			graphics.text(font, line, x, y, 0xFFFFFFFF);
			y += font.lineHeight;
		}
	}

	/** "Librarian · Expert · 5 min ago". */
	private static Component title(TradeMemory.Remembered remembered) {
		MutableComponent title = Component.literal(remembered.title).withStyle(ChatFormatting.YELLOW);
		if (remembered.level >= 1 && remembered.level <= 5) {
			title.append(Component.literal(" · ").append(Component.translatable("merchant.level." + remembered.level)).withStyle(ChatFormatting.GOLD));
		}
		return title.append(Component.literal(" · ").append(age(remembered.seen)).withStyle(ChatFormatting.DARK_GRAY));
	}

	/** "23 Emerald + Book → Enchanted Book (Mending)", grey when it was out of stock. */
	private static Component line(MerchantOffer offer) {
		MutableComponent text = Component.empty().append(amount(offer.getCostA()));
		if (!offer.getCostB().isEmpty()) {
			text.append(" + ").append(amount(offer.getCostB()));
		}
		text.append(" → ").append(amount(offer.getResult()));
		if (offer.isOutOfStock()) {
			return text.append(" ").append(Component.translatable("villager-trade-helper.hud.out_of_stock")).withStyle(ChatFormatting.DARK_GRAY);
		}
		return text.withStyle(ChatFormatting.WHITE);
	}

	/** Counts of 1 left out, so lines stay short. */
	private static Component amount(ItemStack stack) {
		return stack.getCount() == 1 ? Offers.name(stack) : Offers.amount(stack.getCount(), stack);
	}

	public static Component age(long seen) {
		long minutes = Math.max(0, System.currentTimeMillis() - seen) / 60_000;
		if (minutes < 1) {
			return Component.translatable("villager-trade-helper.age.now");
		}
		if (minutes < 60) {
			return Component.translatable("villager-trade-helper.age.minutes", minutes);
		}
		long hours = minutes / 60;
		if (hours < 24) {
			return Component.translatable("villager-trade-helper.age.hours", hours);
		}
		return Component.translatable("villager-trade-helper.age.days", hours / 24);
	}
}
