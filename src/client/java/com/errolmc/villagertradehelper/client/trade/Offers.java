package com.errolmc.villagertradehelper.client.trade;

import com.errolmc.villagertradehelper.trade.TradePlanner;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

/** Reading offers and the trade screen's slots. */
public final class Offers {
	/** Slot indices in {@link MerchantMenu#slots}: the two payment slots, the result, then inventory and hotbar. */
	public static final int PAYMENT_A = 0;
	public static final int PAYMENT_B = 1;
	public static final int RESULT = 2;
	public static final int INVENTORY_START = 3;
	public static final int INVENTORY_END = 39;
	/** Where the hotbar starts; the main inventory is {@link #INVENTORY_START} up to here. */
	public static final int HOTBAR_START = 30;

	private Offers() {
	}

	public static int stockLeft(MerchantOffer offer) {
		return offer.isOutOfStock() ? 0 : Math.max(0, offer.getMaxUses() - offer.getUses());
	}

	/** Items the player has that pay {@code cost}: inventory, hotbar and whatever is already in the payment slots. */
	public static int have(MerchantMenu menu, ItemCost cost) {
		int total = 0;
		for (int i = 0; i < INVENTORY_END; i++) {
			if (i == RESULT) {
				continue;
			}
			ItemStack stack = menu.getSlot(i).getItem();
			if (!stack.isEmpty() && cost.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public static TradePlanner.Estimate estimate(MerchantMenu menu, MerchantOffer offer) {
		int haveB = offer.getItemCostB().map(cost -> have(menu, cost)).orElse(0);
		return TradePlanner.estimate(stockLeft(offer), have(menu, offer.getItemCostA()), offer.getCostA().getCount(), haveB,
			offer.getCostB().getCount());
	}

	/** Trades the items now in the payment slots pay for, as the game matches them (A in slot 0, B in slot 1). */
	public static int tradesInSlots(MerchantMenu menu, MerchantOffer offer) {
		if (menu.getSlot(RESULT).getItem().isEmpty()) {
			return 0;
		}
		ItemStack a = menu.getSlot(PAYMENT_A).getItem();
		ItemStack b = menu.getSlot(PAYMENT_B).getItem();
		int trades = offer.getItemCostA().test(a) ? a.getCount() / Math.max(1, offer.getCostA().getCount()) : 0;
		if (offer.getItemCostB().isPresent()) {
			int costB = Math.max(1, offer.getCostB().getCount());
			trades = Math.min(trades, offer.getItemCostB().get().test(b) ? b.getCount() / costB : 0);
		}
		return trades;
	}

	/** Result items the inventory can still take: free space on stacks of the same item plus a full stack per empty slot. */
	public static int roomFor(MerchantMenu menu, ItemStack result) {
		if (result.isEmpty()) {
			return 0;
		}
		int room = 0;
		for (int i = INVENTORY_START; i < INVENTORY_END; i++) {
			Slot slot = menu.getSlot(i);
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				room += slot.mayPlace(result) ? slot.getMaxStackSize(result) : 0;
			} else if (ItemStack.isSameItemSameComponents(stack, result)) {
				room += Math.max(0, slot.getMaxStackSize(stack) - stack.getCount());
			}
		}
		return room;
	}

	/** Whether {@code trades} more trades' results, and then the payment left in the slots, all fit in the inventory. */
	public static boolean fitsAfter(MerchantMenu menu, MerchantOffer offer, int trades) {
		List<TradePlanner.Held> slots = new ArrayList<>();
		for (int i = INVENTORY_START; i < INVENTORY_END; i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			slots.add(stack.isEmpty() ? TradePlanner.Held.EMPTY : new TradePlanner.Held(new Kind(stack), stack.getCount()));
		}
		ItemStack result = offer.getResult();
		ItemStack a = menu.getSlot(PAYMENT_A).getItem();
		ItemStack b = menu.getSlot(PAYMENT_B).getItem();
		int costB = offer.getCostB().isEmpty() ? 0 : offer.getCostB().getCount();
		List<TradePlanner.Pile> piles = new ArrayList<>();
		addPile(piles, result, trades * result.getCount());
		addPile(piles, a, a.getCount() - trades * offer.getCostA().getCount());
		addPile(piles, b, b.getCount() - trades * costB);
		return TradePlanner.fits(slots, piles);
	}

	private static void addPile(List<TradePlanner.Pile> piles, ItemStack stack, int count) {
		if (!stack.isEmpty() && count > 0) {
			piles.add(new TradePlanner.Pile(new Kind(stack), count, stack.getMaxStackSize()));
		}
	}

	/** Items that stack together: same item, same components. */
	private record Kind(ItemStack stack) {
		@Override
		public boolean equals(Object other) {
			return other instanceof Kind kind && ItemStack.isSameItemSameComponents(stack, kind.stack);
		}

		@Override
		public int hashCode() {
			return stack.getItem().hashCode();
		}
	}

	/** The item's name with any enchantments, for example "Enchanted Book (Mending)". */
	public static Component name(ItemStack stack) {
		MutableComponent enchantments = Component.empty();
		appendEnchantments(enchantments, stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
		appendEnchantments(enchantments, stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
		if (enchantments.getSiblings().isEmpty()) {
			return stack.getHoverName();
		}
		return Component.empty().append(stack.getHoverName()).append(" (").append(enchantments).append(")");
	}

	private static void appendEnchantments(MutableComponent out, ItemEnchantments enchantments) {
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			if (!out.getSiblings().isEmpty()) {
				out.append(", ");
			}
			// getFullname greys the text for tooltips; plain reads better in chat and on the HUD.
			out.append(Component.literal(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString()));
		}
	}

	/** "12 Emerald". */
	public static Component amount(int count, ItemStack stack) {
		return Component.literal(count + " ").append(name(stack));
	}

	/** "4 Iron Ingot → 1 Emerald", or "20 Emerald + 1 Book → 1 Enchanted Book (Mending)". */
	public static Component describe(MerchantOffer offer, int trades) {
		MutableComponent text = Component.empty().append(amount(offer.getCostA().getCount() * trades, offer.getCostA()));
		if (!offer.getCostB().isEmpty()) {
			text.append(" + ").append(amount(offer.getCostB().getCount() * trades, offer.getCostB()));
		}
		return text.append(" → ").append(amount(offer.getResult().getCount() * trades, offer.getResult()));
	}
}
