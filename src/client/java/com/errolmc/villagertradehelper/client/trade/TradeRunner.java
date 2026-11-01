package com.errolmc.villagertradehelper.client.trade;

import com.errolmc.villagertradehelper.client.config.TradeHelperConfig;
import com.errolmc.villagertradehelper.client.mixin.MerchantScreenAccessor;
import com.errolmc.villagertradehelper.trade.TradePlanner;
import com.errolmc.villagertradehelper.trade.TradePlanner.Estimate;
import com.errolmc.villagertradehelper.trade.TradePlanner.OneByOne;
import com.errolmc.villagertradehelper.trade.TradePlanner.Reason;
import com.errolmc.villagertradehelper.trade.TradePlanner.ShiftClick;
import com.errolmc.villagertradehelper.trade.TradePlanner.Step;
import com.errolmc.villagertradehelper.trade.TradePlanner.Stop;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

/**
 * Trades offers over and over with ordinary trade-screen clicks, one batch per tick: select the offer (the game moves
 * up to a stack of payment in), then shift-click the result. Works on any server, since a player could do the same.
 * Decisions use the client's copy of the menu, which the game updates as soon as a click is sent.
 */
public final class TradeRunner {
	/** A running Trade All. */
	private static final class Job {
		final int containerId;
		/** The offer being traded, or -1 once it's done. */
		int offer;
		int cooldown;
		int trades;
		@Nullable Reason reason;
		/** The offer {@link #reason} is about. */
		int reasonOffer = -1;
		boolean interrupted;
		final Map<Item, Tally> given = new LinkedHashMap<>();
		final Map<Item, Tally> got = new LinkedHashMap<>();

		Job(int containerId, int offer) {
			this.containerId = containerId;
			this.offer = offer;
		}
	}

	/** How many of an item went one way, with a stack to name it by. */
	private static final class Tally {
		final ItemStack sample;
		int count;

		Tally(ItemStack sample) {
			this.sample = sample.copyWithCount(1);
		}
	}

	private static @Nullable Job job;
	/** The last finished job's summary, shown above the trade window it ran in for a while. */
	private static @Nullable Component lastSummary;
	private static int lastSummaryContainerId = -1;
	private static long lastSummaryTime;
	private static final long SUMMARY_MILLIS = 10_000;

	private TradeRunner() {
	}

	public static boolean isRunning(MerchantMenu menu) {
		return job != null && job.containerId == menu.containerId;
	}

	/** What to show above the trade window: progress while running, then the summary for a few seconds. */
	public static @Nullable Component status(MerchantMenu menu) {
		if (job != null && job.containerId == menu.containerId) {
			return Component.translatable("villager-trade-helper.message.trading", job.trades).withStyle(ChatFormatting.YELLOW);
		}
		if (lastSummary != null && lastSummaryContainerId == menu.containerId && Util.getMillis() - lastSummaryTime < SUMMARY_MILLIS) {
			return lastSummary;
		}
		return null;
	}

	/** Trades one offer until the villager runs out of it, you run out of payment or your inventory is full. */
	public static void tradeAll(MerchantScreen screen, int offerIndex) {
		Minecraft minecraft = Minecraft.getInstance();
		MerchantMenu menu = screen.getMenu();
		MerchantOffers offers = menu.getOffers();
		if (job != null || offerIndex < 0 || offerIndex >= offers.size()) {
			return;
		}
		MerchantOffer offer = offers.get(offerIndex);
		Estimate estimate = Offers.estimate(menu, offer);
		if (estimate.trades() == 0) {
			// Don't select it: that would only shuffle the payment slots.
			say(minecraft, menu, Component.translatable("villager-trade-helper.message.cannot_trade", reasonText(menu, offer, estimate.limit()))
				.withStyle(ChatFormatting.RED));
			return;
		}
		start(minecraft, new Job(menu.containerId, offerIndex));
	}

	public static void stop() {
		if (job != null) {
			job.interrupted = true;
			job.offer = -1;
		}
	}

	/** The trade screen closed: vanilla puts the payment back into the inventory itself. */
	public static void onScreenClosed(MerchantMenu menu) {
		if (job != null && job.containerId == menu.containerId) {
			job = null;
		}
	}

	private static void start(Minecraft minecraft, Job newJob) {
		job = newJob;
		lastSummary = null;
		tick(minecraft);
	}

	public static void tick(Minecraft minecraft) {
		Job current = job;
		if (current == null) {
			return;
		}
		if (!(minecraft.gui.screen() instanceof MerchantScreen screen) || screen.getMenu().containerId != current.containerId
			|| minecraft.player == null || minecraft.gameMode == null) {
			job = null;
			return;
		}
		if (current.cooldown > 0) {
			current.cooldown--;
			return;
		}
		current.cooldown = TradeHelperConfig.get().ticksBetweenTrades - 1;
		step(minecraft, screen, current);
	}

	private static void step(Minecraft minecraft, MerchantScreen screen, Job current) {
		MerchantMenu menu = screen.getMenu();
		if (current.offer < 0) {
			finish(minecraft, menu, current);
			return;
		}
		MerchantOffers offers = menu.getOffers();
		if (current.offer >= offers.size()) {
			current.offer = -1;
			return;
		}
		MerchantOffer offer = offers.get(current.offer);
		select(screen, current.offer);

		Step step = TradePlanner.next(Offers.stockLeft(offer), Offers.tradesInSlots(menu, offer), trades -> Offers.fitsAfter(menu, offer, trades));
		switch (step) {
			case ShiftClick shiftClick -> {
				int before = offer.getUses();
				click(minecraft, menu, Offers.RESULT, ContainerInput.QUICK_MOVE);
				if (!counted(current, offer, offer.getUses() - before)) {
					current.interrupted = true;
					current.offer = -1;
				}
			}
			case OneByOne oneByOne -> {
				for (int i = 0; i < oneByOne.trades(); i++) {
					if (!takeOne(minecraft, menu, current, offer)) {
						current.interrupted = true;
						current.offer = -1;
						break;
					}
				}
			}
			case Stop stop -> {
				Reason reason = stop.reason();
				// The slots can't pay although the inventory can: the old payment had nowhere to go back to.
				if (reason == Reason.OUT_OF_PAYMENT && Offers.estimate(menu, offer).trades() > 0) {
					reason = Reason.INVENTORY_FULL;
				}
				current.reason = reason;
				current.reasonOffer = current.offer;
				current.offer = -1;
			}
		}
	}

	/** One trade onto the cursor, then put away. Used when the room left would cut a shift-click short. */
	private static boolean takeOne(Minecraft minecraft, MerchantMenu menu, Job current, MerchantOffer offer) {
		if (!menu.getCarried().isEmpty()) {
			return false;
		}
		int before = offer.getUses();
		click(minecraft, menu, Offers.RESULT, ContainerInput.PICKUP);
		if (!counted(current, offer, offer.getUses() - before)) {
			return false;
		}
		while (!menu.getCarried().isEmpty()) {
			int target = placeTarget(menu, menu.getCarried());
			if (target < 0) {
				// Left on the cursor; the game puts it back into the inventory when the screen closes.
				return false;
			}
			click(minecraft, menu, target, ContainerInput.PICKUP);
		}
		return true;
	}

	/** A stack of the same item with room, else an empty slot; main inventory before the hotbar. */
	private static int placeTarget(MerchantMenu menu, ItemStack carried) {
		int empty = -1;
		for (int i = Offers.INVENTORY_START; i < Offers.INVENTORY_END; i++) {
			Slot slot = menu.getSlot(i);
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				if (empty < 0 && slot.mayPlace(carried)) {
					empty = i;
				}
			} else if (ItemStack.isSameItemSameComponents(stack, carried) && stack.getCount() < slot.getMaxStackSize(stack)) {
				return i;
			}
		}
		return empty;
	}

	/** Adds {@code trades} to the totals; false when nothing was traded. */
	private static boolean counted(Job current, MerchantOffer offer, int trades) {
		if (trades <= 0) {
			return false;
		}
		current.trades += trades;
		add(current.given, offer.getCostA(), offer.getCostA().getCount() * trades);
		if (!offer.getCostB().isEmpty()) {
			add(current.given, offer.getCostB(), offer.getCostB().getCount() * trades);
		}
		add(current.got, offer.getResult(), offer.getResult().getCount() * trades);
		return true;
	}

	private static void add(Map<Item, Tally> tallies, ItemStack stack, int count) {
		tallies.computeIfAbsent(stack.getItem(), item -> new Tally(stack)).count += count;
	}

	private static void select(MerchantScreen screen, int offerIndex) {
		MerchantScreenAccessor accessor = (MerchantScreenAccessor) screen;
		accessor.villagerTradeHelper$setShopItem(offerIndex);
		accessor.villagerTradeHelper$postButtonClick();
	}

	private static void click(Minecraft minecraft, MerchantMenu menu, int slot, ContainerInput input) {
		minecraft.gameMode.handleContainerInput(menu.containerId, slot, 0, input, minecraft.player);
	}

	private static void finish(Minecraft minecraft, MerchantMenu menu, Job current) {
		// Put leftover payment back, so the inventory looks the way it will after closing.
		for (int slot : new int[] {Offers.PAYMENT_A, Offers.PAYMENT_B}) {
			if (menu.getSlot(slot).hasItem()) {
				click(minecraft, menu, slot, ContainerInput.QUICK_MOVE);
			}
		}
		job = null;
		say(minecraft, menu, summary(menu, current));
	}

	private static Component summary(MerchantMenu menu, Job current) {
		Component why = null;
		if (current.interrupted) {
			why = Component.translatable("villager-trade-helper.reason.stopped");
		} else if (current.reason != null && current.reasonOffer < menu.getOffers().size()) {
			why = reasonText(menu, menu.getOffers().get(current.reasonOffer), current.reason);
		}
		if (current.trades == 0) {
			return Component.translatable("villager-trade-helper.message.cannot_trade",
				why != null ? why : Component.translatable("villager-trade-helper.reason.stopped")).withStyle(ChatFormatting.RED);
		}
		MutableComponent text = Component.translatable("villager-trade-helper.message.traded", current.trades, list(current.given), list(current.got))
			.withStyle(ChatFormatting.GREEN);
		if (why != null) {
			text.append(Component.literal(" · ").append(why).withStyle(ChatFormatting.GRAY));
		}
		return text;
	}

	private static Component list(Map<Item, Tally> tallies) {
		MutableComponent text = Component.empty();
		boolean first = true;
		for (Tally tally : tallies.values()) {
			if (!first) {
				text.append(", ");
			}
			text.append(Offers.amount(tally.count, tally.sample));
			first = false;
		}
		return text;
	}

	/** "out of stock", "you ran out of Iron Ingot", "inventory full". */
	public static Component reasonText(MerchantMenu menu, MerchantOffer offer, Reason reason) {
		return switch (reason) {
			case OUT_OF_STOCK -> Component.translatable("villager-trade-helper.reason.out_of_stock");
			case INVENTORY_FULL -> Component.translatable("villager-trade-helper.reason.inventory_full");
			case OUT_OF_PAYMENT -> {
				ItemStack missing = Offers.have(menu, offer.getItemCostA()) < offer.getCostA().getCount() || offer.getCostB().isEmpty()
					? offer.getCostA() : offer.getCostB();
				yield Component.translatable("villager-trade-helper.reason.out_of_payment", Offers.name(missing));
			}
		};
	}

	private static void say(Minecraft minecraft, MerchantMenu menu, Component message) {
		lastSummary = message;
		lastSummaryContainerId = menu.containerId;
		lastSummaryTime = Util.getMillis();
		if (minecraft.player != null && TradeHelperConfig.get().chatSummary) {
			minecraft.player.sendSystemMessage(message);
		}
	}
}
