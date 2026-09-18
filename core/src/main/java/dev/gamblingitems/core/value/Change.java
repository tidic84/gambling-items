package dev.gamblingitems.core.value;

import java.util.Objects;

/**
 * Turns an amount of value back into items, so a game can pay a win in whatever the catalogue holds
 * rather than in one imposed material.
 *
 * <p>The largest item that still fits is taken first, then the next one, and so on. What remains
 * below the cheapest item cannot be paid in items and is dropped, which is the rounding every game
 * announces. Because the largest fitting item is always taken, that remainder is always smaller
 * than the cheapest item of the catalogue.
 */
public final class Change {
    private Change() {}

    /** One payment: how many of each denomination, and what could not be paid in items. */
    public record Payment(int[] counts, long remainder, int items) {
        public Payment {
            Objects.requireNonNull(counts, "counts");
            if (remainder < 0 || items < 0) throw new IllegalArgumentException("Invalid payment");
        }

        public boolean isEmpty() { return items == 0; }
    }

    /**
     * Pays {@code amount} with the given values, sorted ascending, using at most {@code maxItems}
     * items. What could not be paid stays in the remainder; nothing is ever over-paid.
     */
    public static Payment make(long amount, long[] valuesAscending, int maxItems) {
        Objects.requireNonNull(valuesAscending, "valuesAscending");
        if (amount < 0 || maxItems < 0) throw new IllegalArgumentException("Invalid payment request");
        if (valuesAscending.length == 0) throw new IllegalArgumentException("An empty catalogue pays nothing");
        for (int index = 0; index < valuesAscending.length; index++) {
            if (valuesAscending[index] <= 0
                    || (index > 0 && valuesAscending[index] < valuesAscending[index - 1])) {
                throw new IllegalArgumentException("Values must be positive and sorted ascending");
            }
        }
        int[] counts = new int[valuesAscending.length];
        long left = amount;
        int items = 0;
        for (int index = valuesAscending.length - 1; index >= 0 && items < maxItems; index--) {
            long value = valuesAscending[index];
            if (value > left) continue;
            long wanted = left / value;
            long taken = Math.min(wanted, maxItems - items);
            counts[index] = (int) taken;
            items += (int) taken;
            left -= taken * value;
        }
        return new Payment(counts, left, items);
    }

    /** How many items a payment of this amount would need, without any limit getting in the way. */
    public static int itemsNeeded(long amount, long[] valuesAscending) {
        return make(amount, valuesAscending, Integer.MAX_VALUE).items();
    }

    /**
     * The largest amount that can be paid with at most {@code maxItems} items.
     * Used to refuse a bet whose win could not be handed over, before anything is engaged.
     */
    public static long payableWith(long[] valuesAscending, int maxItems) {
        if (maxItems <= 0) return 0;
        return Math.multiplyExact(valuesAscending[valuesAscending.length - 1], (long) maxItems);
    }
}
