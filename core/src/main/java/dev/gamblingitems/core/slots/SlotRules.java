package dev.gamblingitems.core.slots;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.IntUnaryOperator;

/**
 * Pure rules of a slot machine: three reels of the same eight symbols, drawn independently.
 * Three alike pay the line of that symbol, two alike pay a consolation, anything else pays nothing.
 *
 * <p>Multipliers are hundredths of the stake and include the stake, so 400 gives four times what
 * was played. The paytable is printed here rather than configured: the odds of a cabinet are the
 * same for everyone, and {@link #returnToPlayer()} can be read off it by anyone who wants to check.
 */
public final class SlotRules {
    /** How many reels turn, and how many symbols each of them carries. */
    public static final int REELS = 3;
    public static final int SYMBOLS = 8;
    /** What three of a kind pays, in hundredths of the stake, cheapest symbol first. */
    private static final int[] TRIPLE = {400, 500, 600, 900, 1_500, 3_000, 6_000, 12_000};
    /** What two of a kind pays, whichever symbol they are. */
    public static final int PAIR = 140;

    private SlotRules() {}

    /** The three symbols one pull of the lever shows, drawn one reel at a time. */
    public static int[] spin(IntUnaryOperator draw) {
        int[] reels = new int[REELS];
        for (int reel = 0; reel < REELS; reel++) {
            int symbol = draw.applyAsInt(SYMBOLS);
            if (symbol < 0 || symbol >= SYMBOLS) throw new IllegalArgumentException("Symbol outside the reel");
            reels[reel] = symbol;
        }
        return reels;
    }

    /** What three of a kind of this symbol pays, in hundredths of the stake. */
    public static int tripleOf(int symbol) {
        if (symbol < 0 || symbol >= SYMBOLS) throw new IllegalArgumentException("Unknown symbol: " + symbol);
        return TRIPLE[symbol];
    }

    /** What this line pays, in hundredths of the stake; zero when it pays nothing. */
    public static int multiplier(int[] reels) {
        if (reels == null || reels.length != REELS) throw new IllegalArgumentException("A line has three reels");
        for (int symbol : reels) {
            if (symbol < 0 || symbol >= SYMBOLS) throw new IllegalArgumentException("Symbol outside the reel");
        }
        if (reels[0] == reels[1] && reels[1] == reels[2]) return TRIPLE[reels[0]];
        return reels[0] == reels[1] || reels[1] == reels[2] || reels[0] == reels[2] ? PAIR : 0;
    }

    /** Everything a line pays, stake included, rounded down as announced. */
    public static long payout(long stake, int multiplier, BigDecimal returnRate) {
        if (stake <= 0 || multiplier < 0) throw new IllegalArgumentException("Invalid slot settlement");
        if (returnRate == null || returnRate.signum() <= 0 || returnRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Invalid return rate");
        }
        if (multiplier == 0) return 0;
        return BigDecimal.valueOf(stake).multiply(BigDecimal.valueOf(multiplier)).multiply(returnRate)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).longValueExact();
    }

    /** The largest line of the paytable, so a cabinet can refuse a bet it could not pay. */
    public static int bestMultiplier() {
        int best = PAIR;
        for (int value : TRIPLE) best = Math.max(best, value);
        return best;
    }

    /** The share of everything played that the paytable gives back, counted over every line. */
    public static BigDecimal returnToPlayer() {
        long total = 0;
        int lines = SYMBOLS * SYMBOLS * SYMBOLS;
        for (int first = 0; first < SYMBOLS; first++) {
            for (int second = 0; second < SYMBOLS; second++) {
                for (int third = 0; third < SYMBOLS; third++) {
                    total += multiplier(new int[] {first, second, third});
                }
            }
        }
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(100L * lines), 4, RoundingMode.HALF_UP);
    }
}
