package dev.gamblingitems.core.roulette;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure rules of the short roulette: a wheel of coloured slots, and what each colour pays.
 * The spin itself, the phases and the payments belong to the server integration.
 *
 * <p>Payouts include the stake, as announced on the screen. With seven red, seven black and one
 * green slot paying two and fourteen times, the three bets return the same 14/15 of what they stake.
 */
public record RouletteRules(int redSlots, int blackSlots, int greenSlots,
                            int colourPayout, int greenPayout) {
    public static final int MAX_SLOTS = 64;

    public enum Colour {
        RED("red"), BLACK("black"), GREEN("green");

        private final String id;
        Colour(String id) { this.id = id; }
        public String id() { return id; }
        public static Colour fromId(String id) {
            for (Colour colour : values()) if (colour.id.equals(id)) return colour;
            throw new IllegalArgumentException("Unknown roulette colour: " + id);
        }
    }

    public RouletteRules {
        if (redSlots < 1 || blackSlots < 1 || greenSlots < 1
                || redSlots + blackSlots + greenSlots > MAX_SLOTS
                || colourPayout < 2 || greenPayout < 2
                || colourPayout > MAX_SLOTS || greenPayout > MAX_SLOTS) {
            throw new IllegalArgumentException("Invalid roulette settings");
        }
    }

    public int slots() { return redSlots + blackSlots + greenSlots; }

    /**
     * The wheel as players read it: the green slots first, then red and black alternating.
     * The order is fixed so every screen draws the same wheel as the server span.
     */
    public Colour colourAt(int slot) {
        if (slot < 0 || slot >= slots()) throw new IllegalArgumentException("Slot outside the wheel");
        if (slot < greenSlots) return Colour.GREEN;
        int position = slot - greenSlots;
        boolean red = position % 2 == 0;
        int redsBefore = (position + 1) / 2;
        int blacksBefore = position / 2;
        // Once one colour runs out, the rest of the wheel is the other one.
        if (red && redsBefore < redSlots) return Colour.RED;
        if (!red && blacksBefore < blackSlots) return Colour.BLACK;
        return red ? Colour.BLACK : Colour.RED;
    }

    public int slotsOf(Colour colour) {
        return switch (colour) {
            case RED -> redSlots;
            case BLACK -> blackSlots;
            case GREEN -> greenSlots;
        };
    }

    public int payoutOf(Colour colour) { return colour == Colour.GREEN ? greenPayout : colourPayout; }

    /** The caller supplies an independent server-side draw in [0, bound). */
    public int spin(long draw, long bound) {
        if (bound < 1 || draw < 0 || draw >= bound) throw new IllegalArgumentException("draw is outside the wheel");
        return (int) (draw * slots() / bound);
    }

    /** Everything the player receives, stake included; zero when the colour did not come out. */
    public long payout(long stake, Colour bet, Colour result) {
        if (stake <= 0) throw new IllegalArgumentException("A bet needs a positive stake");
        return bet == result ? Math.multiplyExact(stake, payoutOf(bet)) : 0;
    }

    /** What a colour returns on average. Shown, never silently corrected. */
    public BigDecimal returnRate(Colour colour) {
        return BigDecimal.valueOf((long) slotsOf(colour) * payoutOf(colour))
                .divide(BigDecimal.valueOf(slots()), 6, RoundingMode.HALF_UP);
    }

    public BigDecimal chanceOf(Colour colour) {
        return BigDecimal.valueOf(slotsOf(colour) * 100L)
                .divide(BigDecimal.valueOf(slots()), 4, RoundingMode.HALF_UP);
    }

    /** The largest payout a stake could win here, used to check it could be stored. */
    public long maximumPayout(long stake) { return Math.multiplyExact(stake, Math.max(colourPayout, greenPayout)); }
}
