package dev.gamblingitems.fabric.menu;

import net.minecraft.world.inventory.ContainerData;

/**
 * Sends an item value to a screen through the data slots of a menu.
 *
 * <p>A data slot travels as a short: anything above 32767 comes back negative on the client, which
 * is exactly what a win of a few diamonds is worth once counted in values. Every value is therefore
 * split over two slots of fifteen bits and put back together on the other side.
 */
public final class ValueSync {
    /** How many data slots one value needs. */
    public static final int SLOTS = 2;
    private static final int CHUNK = 15;
    private static final int MASK = (1 << CHUNK) - 1;
    /** The largest value that survives the trip, which is above any catalogue this mod accepts. */
    public static final long MAX = (1L << (2 * CHUNK)) - 1;

    private ValueSync() {}

    /** Writes a value into two slots. A value beyond what can be sent is capped, never wrapped. */
    public static void write(ContainerData data, int index, long value) {
        long bounded = Math.max(0, Math.min(MAX, value));
        data.set(index, (int) (bounded & MASK));
        data.set(index + 1, (int) ((bounded >> CHUNK) & MASK));
    }

    /** Reads back a value written by {@link #write}, whatever the packet did to the slots. */
    public static long read(ContainerData data, int index) {
        long low = data.get(index) & MASK;
        long high = data.get(index + 1) & MASK;
        return (high << CHUNK) | low;
    }
}
