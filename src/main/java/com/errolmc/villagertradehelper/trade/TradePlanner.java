package com.errolmc.villagertradehelper.trade;

import java.util.List;
import java.util.function.IntPredicate;

/**
 * Decides how many times an offer can be traded and how the next batch is clicked. Pure Java (no Minecraft), so the
 * rules are unit tested; the client reads the counts off the open trade screen.
 *
 * <p>A batch starts after the trade has been selected, which makes the game move up to a stack of each payment item
 * from the inventory into the payment slots. Shift-clicking the result slot then trades until those run out, the
 * villager runs out, or a result no longer fits. A result that only partly fits is lost (vanilla puts what fits away,
 * takes the full price and replaces the result slot), so when the room runs out first the remaining trades are taken
 * one at a time instead. Room is also kept for the payment left in the slots: it goes back into the inventory at the
 * end, and vanilla drops it on the ground when the screen closes and it doesn't fit.
 */
public final class TradePlanner {
	/** Why trading an offer stopped, or would stop. */
	public enum Reason {
		OUT_OF_STOCK,
		OUT_OF_PAYMENT,
		INVENTORY_FULL
	}

	/** What to do once the payment slots have been filled. */
	public sealed interface Step permits ShiftClick, OneByOne, Stop {
	}

	/** Shift-click the result slot; vanilla repeats the trade {@code trades} times. */
	public record ShiftClick(int trades) implements Step {
	}

	/** Take the result onto the cursor and put it away, {@code trades} times, so no result is cut short. */
	public record OneByOne(int trades) implements Step {
	}

	public record Stop(Reason reason) implements Step {
	}

	/** How often an offer can be traded with what the player has, and what limits it. */
	public record Estimate(int trades, Reason limit) {
	}

	/** What an inventory slot holds: a kind of item (compared with {@code equals}, null when empty) and how many. */
	public record Held(Object kind, int count) {
		public static final Held EMPTY = new Held(null, 0);
	}

	/** Items to put away: {@code count} of {@code kind}, at most {@code maxStack} to a slot. */
	public record Pile(Object kind, int count, int maxStack) {
	}

	private TradePlanner() {
	}

	/**
	 * @param stockLeft     trades the villager has left for the offer
	 * @param tradesInSlots trades the items now in the payment slots pay for
	 * @param fitsAfter     whether the results of that many more trades, and then the payment left in the slots, all
	 *                      fit in the inventory (see {@link #fits})
	 */
	public static Step next(int stockLeft, int tradesInSlots, IntPredicate fitsAfter) {
		if (stockLeft <= 0) {
			return new Stop(Reason.OUT_OF_STOCK);
		}
		if (tradesInSlots <= 0) {
			return new Stop(Reason.OUT_OF_PAYMENT);
		}
		int batch = Math.min(stockLeft, tradesInSlots);
		if (fitsAfter.test(batch)) {
			return new ShiftClick(batch);
		}
		// Fewer trades leave more payment to put back, so look for the most that fit rather than stopping at the first.
		for (int trades = batch - 1; trades > 0; trades--) {
			if (fitsAfter.test(trades)) {
				return new OneByOne(trades);
			}
		}
		return new Stop(Reason.INVENTORY_FULL);
	}

	/**
	 * Whether the piles, in order, all fit into the slots the way the game puts items away: onto stacks of the same
	 * kind first, then into empty slots.
	 */
	public static boolean fits(List<Held> slots, List<Pile> piles) {
		Object[] kinds = new Object[slots.size()];
		int[] counts = new int[slots.size()];
		for (int i = 0; i < kinds.length; i++) {
			kinds[i] = slots.get(i).kind();
			counts[i] = slots.get(i).count();
		}
		for (Pile pile : piles) {
			int left = pile.count();
			for (int i = 0; i < kinds.length && left > 0; i++) {
				if (pile.kind().equals(kinds[i]) && counts[i] < pile.maxStack()) {
					int added = Math.min(left, pile.maxStack() - counts[i]);
					counts[i] += added;
					left -= added;
				}
			}
			for (int i = 0; i < kinds.length && left > 0; i++) {
				if (kinds[i] == null) {
					kinds[i] = pile.kind();
					counts[i] = Math.min(left, pile.maxStack());
					left -= counts[i];
				}
			}
			if (left > 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Trades the player can afford, capped by the villager's stock. Ignores inventory room, which grows as the payment
	 * leaves the inventory, so it is only known while trading.
	 *
	 * @param costB 0 when the offer has no second cost
	 */
	public static Estimate estimate(int stockLeft, int haveA, int costA, int haveB, int costB) {
		if (stockLeft <= 0) {
			return new Estimate(0, Reason.OUT_OF_STOCK);
		}
		int affordable = haveA / Math.max(1, costA);
		if (costB > 0) {
			affordable = Math.min(affordable, haveB / costB);
		}
		return affordable < stockLeft
			? new Estimate(affordable, Reason.OUT_OF_PAYMENT)
			: new Estimate(stockLeft, Reason.OUT_OF_STOCK);
	}
}
