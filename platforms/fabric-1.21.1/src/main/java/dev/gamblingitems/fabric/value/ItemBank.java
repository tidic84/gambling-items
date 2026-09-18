package dev.gamblingitems.fabric.value;

import dev.gamblingitems.core.value.Change;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Reads and writes value in items, so a game can take any priced item as a stake and pay a win
 * back in items instead of in one imposed material.
 *
 * <p>A payment is made of the dearest items that still fit, from this server's catalogue.
 * What remains below the cheapest item cannot be handed over and is dropped: that is the rounding
 * every game announces.
 */
public final class ItemBank {
    private ItemBank() {}

    private record Denomination(ValueCatalog.Entry entry, long value) {}

    /** Distinct values of the catalogue, ascending, with one item chosen for each. */
    private static List<Denomination> denominations(ValueCatalog catalog) {
        List<Denomination> denominations = new ArrayList<>();
        long previous = -1;
        for (ValueCatalog.Entry entry : catalog.entries()) {
            if (entry.value() == previous) continue;
            previous = entry.value();
            denominations.add(new Denomination(entry, entry.value()));
        }
        return denominations;
    }

    private static long[] values(List<Denomination> denominations) {
        long[] values = new long[denominations.size()];
        for (int index = 0; index < values.length; index++) values[index] = denominations.get(index).value();
        return values;
    }

    /** Total value of a range of slots. Unpriced items count for nothing and are never taken. */
    public static long valueOf(ValueCatalog catalog, Container container, int from, int to) {
        long total = 0;
        for (int slot = from; slot < to; slot++) total += catalog.valueOf(container.getItem(slot));
        return total;
    }

    /** Empty slots of a range, the only place a payment can be written. */
    public static int freeSlots(Container container, int from, int to) {
        int free = 0;
        for (int slot = from; slot < to; slot++) {
            if (container.getItem(slot).isEmpty()) free++;
        }
        return free;
    }

    /** True when this amount could be handed over right now, counting only the empty slots. */
    public static boolean canStore(ValueCatalog catalog, Container container, int from, int to, long amount) {
        return slotsNeeded(catalog, amount) <= freeSlots(container, from, to);
    }

    /** How many slots a payment of this amount would occupy, without merging into existing stacks. */
    public static int slotsNeeded(ValueCatalog catalog, long amount) {
        if (amount <= 0) return 0;
        List<Denomination> denominations = denominations(catalog);
        Change.Payment payment = Change.make(amount, values(denominations), Integer.MAX_VALUE);
        int slots = 0;
        for (int index = 0; index < payment.counts().length; index++) {
            int count = payment.counts()[index];
            if (count == 0) continue;
            int limit = denominations.get(index).entry().stack().getMaxStackSize();
            slots += (count + limit - 1) / limit;
        }
        return slots;
    }

    /**
     * Writes a payment into the empty slots of a range and returns the value that could not be paid:
     * what was below the cheapest item, plus anything that did not fit.
     */
    public static long store(ValueCatalog catalog, Container container, int from, int to, long amount) {
        if (amount <= 0) return Math.max(0, amount);
        List<Denomination> denominations = denominations(catalog);
        Change.Payment payment = Change.make(amount, values(denominations), Integer.MAX_VALUE);
        long unpaid = payment.remainder();
        int slot = from;
        for (int index = payment.counts().length - 1; index >= 0; index--) {
            int left = payment.counts()[index];
            if (left == 0) continue;
            Denomination denomination = denominations.get(index);
            int limit = denomination.entry().stack().getMaxStackSize();
            while (left > 0) {
                while (slot < to && !container.getItem(slot).isEmpty()) slot++;
                if (slot >= to) {
                    // The room was checked before the bet; anything left here is reported, never lost silently.
                    return unpaid + (long) left * denomination.value();
                }
                int placed = Math.min(left, limit);
                ItemStack stack = denomination.entry().stack();
                stack.setCount(placed);
                container.setItem(slot, stack);
                left -= placed;
            }
        }
        return unpaid;
    }

    /** Moves every stack of a range into another one, slot by slot. Used to engage or return a stake. */
    public static void move(Container container, int fromStart, int toStart, int count) {
        for (int offset = 0; offset < count; offset++) {
            ItemStack stack = container.getItem(fromStart + offset);
            container.setItem(fromStart + offset, ItemStack.EMPTY);
            container.setItem(toStart + offset, stack);
        }
    }

    /** True when every slot of the range is empty. */
    public static boolean isEmpty(Container container, int from, int to) {
        for (int slot = from; slot < to; slot++) {
            if (!container.getItem(slot).isEmpty()) return false;
        }
        return true;
    }
}
