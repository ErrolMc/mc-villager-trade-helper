package com.errolmc.villagertradehelper.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.errolmc.villagertradehelper.trade.TradePlanner.Estimate;
import com.errolmc.villagertradehelper.trade.TradePlanner.Held;
import com.errolmc.villagertradehelper.trade.TradePlanner.OneByOne;
import com.errolmc.villagertradehelper.trade.TradePlanner.Pile;
import com.errolmc.villagertradehelper.trade.TradePlanner.Reason;
import com.errolmc.villagertradehelper.trade.TradePlanner.ShiftClick;
import com.errolmc.villagertradehelper.trade.TradePlanner.Stop;
import java.util.List;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

class TradePlannerTest {
	private static final IntPredicate ALWAYS_FITS = trades -> true;

	@Test
	void shiftClicksWhenEverythingFits() {
		// 64 iron in the slot at 4 each pays for 16, the villager has 12 left.
		assertEquals(new ShiftClick(12), TradePlanner.next(12, 16, ALWAYS_FITS));
		assertEquals(new ShiftClick(16), TradePlanner.next(30, 16, ALWAYS_FITS));
	}

	@Test
	void stopsWhenOutOfStockFirst() {
		assertEquals(new Stop(Reason.OUT_OF_STOCK), TradePlanner.next(0, 16, ALWAYS_FITS));
		assertEquals(new Stop(Reason.OUT_OF_STOCK), TradePlanner.next(-3, 0, ALWAYS_FITS));
	}

	@Test
	void stopsWhenThePaymentSlotsCannotPay() {
		assertEquals(new Stop(Reason.OUT_OF_PAYMENT), TradePlanner.next(12, 0, ALWAYS_FITS));
	}

	@Test
	void stopsWhenNotEvenOneTradeFits() {
		assertEquals(new Stop(Reason.INVENTORY_FULL), TradePlanner.next(12, 16, trades -> trades == 0));
	}

	@Test
	void takesTheMostTradesThatFitOneByOne() {
		assertEquals(new OneByOne(2), TradePlanner.next(12, 64, trades -> trades <= 2));
		// Not monotonic: 3 trades leave too much payment to put back, 4 use enough of it up.
		assertEquals(new OneByOne(4), TradePlanner.next(5, 64, trades -> trades == 4 || trades == 1));
	}

	@Test
	void fitsFillsSameStacksBeforeEmptySlots() {
		// 60 bread and one empty slot: room for 4 + 64 bread.
		List<Held> slots = List.of(new Held("bread", 60), Held.EMPTY, new Held("stone", 64));
		assertTrue(TradePlanner.fits(slots, List.of(new Pile("bread", 68, 64))));
		assertFalse(TradePlanner.fits(slots, List.of(new Pile("bread", 69, 64))));
		// The empty slot takes only one kind.
		assertFalse(TradePlanner.fits(slots, List.of(new Pile("bread", 5, 64), new Pile("emerald", 1, 64))));
		assertTrue(TradePlanner.fits(slots, List.of(new Pile("bread", 4, 64), new Pile("emerald", 64, 64))));
	}

	@Test
	void fitsRespectsUnstackableItems() {
		List<Held> slots = List.of(Held.EMPTY, Held.EMPTY, Held.EMPTY);
		assertTrue(TradePlanner.fits(slots, List.of(new Pile("book", 3, 1))));
		assertFalse(TradePlanner.fits(slots, List.of(new Pile("book", 3, 1), new Pile("emerald", 1, 64))));
		assertTrue(TradePlanner.fits(slots, List.of(new Pile("book", 2, 1), new Pile("emerald", 40, 64))));
	}

	@Test
	void fitsWithNothingToPlace() {
		assertTrue(TradePlanner.fits(List.of(), List.of()));
		assertTrue(TradePlanner.fits(List.of(new Held("stone", 64)), List.of(new Pile("stone", 0, 64))));
	}

	@Test
	void estimateIsCappedByStockOrPayment() {
		assertEquals(new Estimate(12, Reason.OUT_OF_STOCK), TradePlanner.estimate(12, 100, 4, 0, 0));
		assertEquals(new Estimate(2, Reason.OUT_OF_PAYMENT), TradePlanner.estimate(16, 40, 15, 0, 0));
		assertEquals(new Estimate(0, Reason.OUT_OF_STOCK), TradePlanner.estimate(0, 100, 4, 0, 0));
		assertEquals(new Estimate(0, Reason.OUT_OF_PAYMENT), TradePlanner.estimate(12, 3, 4, 0, 0));
	}

	@Test
	void estimateUsesTheScarcerOfTwoCosts() {
		// Enchanted book: 20 emeralds and 1 book each. 100 emeralds but 3 books.
		assertEquals(new Estimate(3, Reason.OUT_OF_PAYMENT), TradePlanner.estimate(12, 100, 20, 3, 1));
		// Enough books, 45 emeralds: 2.
		assertEquals(new Estimate(2, Reason.OUT_OF_PAYMENT), TradePlanner.estimate(12, 45, 20, 10, 1));
		// Exactly the stock is limited by stock.
		assertEquals(new Estimate(1, Reason.OUT_OF_STOCK), TradePlanner.estimate(1, 45, 20, 10, 1));
	}
}
