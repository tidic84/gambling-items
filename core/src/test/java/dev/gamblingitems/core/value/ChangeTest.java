package dev.gamblingitems.core.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChangeTest {
    private static final long[] CATALOGUE = {100, 1_000, 10_000, 90_000};

    @Test
    void paysWithTheLargestItemsFirst() {
        Change.Payment payment = Change.make(92_300, CATALOGUE, 100);
        assertArrayEquals(new int[] {3, 2, 0, 1}, payment.counts(), "One block, two ingots, three nuggets");
        assertEquals(0, payment.remainder());
        assertEquals(6, payment.items());
    }

    @Test
    void whatIsBelowTheCheapestItemCannotBePaid() {
        Change.Payment payment = Change.make(1_099, CATALOGUE, 100);
        assertEquals(99, payment.remainder(), "The rounding announced by every game");
        assertEquals(1, payment.items(), "One ingot, and the rest is too small to pay");
        assertTrue(payment.remainder() < CATALOGUE[0], "A remainder never reaches the cheapest item");
    }

    @Test
    void aLimitOfItemsNeverOverPays() {
        Change.Payment payment = Change.make(270_000, CATALOGUE, 2);
        assertEquals(2, payment.items(), "Only two items fit");
        assertEquals(90_000, payment.remainder(), "The rest is simply not paid");
        assertArrayEquals(new int[] {0, 0, 0, 2}, payment.counts());
    }

    @Test
    void nothingToPayIsAnEmptyPayment() {
        Change.Payment payment = Change.make(0, CATALOGUE, 10);
        assertTrue(payment.isEmpty());
        assertEquals(0, payment.remainder());
        assertEquals(0, Change.make(50, CATALOGUE, 10).items(), "Below the cheapest item, nothing is paid");
        assertEquals(50, Change.make(50, CATALOGUE, 10).remainder());
    }

    @Test
    void theNumberOfItemsIsKnownBeforePaying() {
        assertEquals(6, Change.itemsNeeded(92_300, CATALOGUE));
        assertEquals(0, Change.itemsNeeded(99, CATALOGUE));
        assertEquals(90_000L * 10, Change.payableWith(CATALOGUE, 10), "Ten of the dearest item");
        assertEquals(0, Change.payableWith(CATALOGUE, 0));
    }

    @Test
    void refusesImpossibleRequests() {
        assertThrows(IllegalArgumentException.class, () -> Change.make(-1, CATALOGUE, 10));
        assertThrows(IllegalArgumentException.class, () -> Change.make(10, CATALOGUE, -1));
        assertThrows(IllegalArgumentException.class, () -> Change.make(10, new long[0], 10));
        assertThrows(IllegalArgumentException.class, () -> Change.make(10, new long[] {5, 1}, 10));
        assertThrows(IllegalArgumentException.class, () -> Change.make(10, new long[] {0, 1}, 10));
    }
}
