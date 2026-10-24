package dev.gamblingitems.core.machines;

import dev.gamblingitems.core.GameMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Pure rules shared by the prize machines: what one play can show and what it pays. Every machine is
 * the same settlement, a multiplier in hundredths of the stake, stake included, drawn from a table of
 * weights; the machines only differ in how that table is read.
 *
 * <ul>
 *   <li>Lucky wheel, coin pusher, capsule gacha: one prize per entry, drawn by its weight. A wheel
 *   gives each sector an angle in proportion to its weight, so what is drawn is what is shown.</li>
 *   <li>Prize claw: one prize per position, and its weight is the grip, the chance in ten thousand
 *   that the claw holds it. The player chooses the position.</li>
 *   <li>Pachinko: a ball falls through {@link #PACHINKO_ROWS} rows of pins, left or right at each,
 *   into one of nine pockets. The weights are the binomial ones, not configured.</li>
 *   <li>High stakes terminal: the player chooses a target multiplier; it is reached with the exact
 *   chance of 100 in target, so every target gives back the whole stake on average before the
 *   return rate of the machine.</li>
 * </ul>
 */
public record MachineTable(GameMode mode, int[] multipliers, int[] weights) {
    public static final int PACHINKO_ROWS = 8, PACHINKO_POCKETS = PACHINKO_ROWS + 1;
    /** One grip is counted in ten thousandths, as the return rates are. */
    public static final int GRIP_SCALE = 10_000;
    public static final int MAX_ENTRIES = 24, MAX_MULTIPLIER = 1_000_000;
    private static final long MAX_TOTAL_WEIGHT = 1_000_000_000L;

    /** A prize of the table, in hundredths of the stake, and its weight among the others. */
    public record Prize(int multiplier, int weight) {}

    /** One play: what it pays, and what the machine shows of it, the same for every viewer. */
    public record Outcome(int multiplier, int[] shown) {}

    public MachineTable {
        if (mode == null || multipliers == null || weights == null) throw new IllegalArgumentException("Incomplete machine table");
        multipliers = multipliers.clone();
        weights = weights.clone();
        if (multipliers.length == 0 || multipliers.length > MAX_ENTRIES) {
            throw new IllegalArgumentException("A machine has between 1 and " + MAX_ENTRIES + " prizes, not " + multipliers.length);
        }
        for (int multiplier : multipliers) {
            if (multiplier < 0 || multiplier > MAX_MULTIPLIER) {
                throw new IllegalArgumentException("A multiplier must be between 0 and " + MAX_MULTIPLIER + " hundredths, not " + multiplier);
            }
        }
        switch (mode) {
            case LUCKY_WHEEL, COIN_PUSHER, CAPSULE_GACHA -> {
                requireSameLength(multipliers, weights);
                long total = 0;
                for (int weight : weights) {
                    if (weight < 0) throw new IllegalArgumentException("A weight cannot be negative: " + weight);
                    total += weight;
                }
                if (total <= 0 || total > MAX_TOTAL_WEIGHT) {
                    throw new IllegalArgumentException("The weights must add up to between 1 and " + MAX_TOTAL_WEIGHT + ", not " + total);
                }
            }
            case PRIZE_CLAW -> {
                requireSameLength(multipliers, weights);
                if (multipliers.length > 9) throw new IllegalArgumentException("A claw has at most 9 positions");
                for (int grip : weights) {
                    if (grip < 0 || grip > GRIP_SCALE) {
                        throw new IllegalArgumentException("A grip is between 0 and " + GRIP_SCALE + ", not " + grip);
                    }
                }
            }
            case PACHINKO -> {
                if (multipliers.length != PACHINKO_POCKETS) {
                    throw new IllegalArgumentException("A pachinko board has " + PACHINKO_POCKETS + " pockets, not " + multipliers.length);
                }
                weights = binomial();
            }
            case HIGH_STAKES -> {
                for (int target : multipliers) {
                    if (target <= 100) throw new IllegalArgumentException("A target must be above 1x (100), not " + target);
                }
                weights = new int[multipliers.length];
            }
            default -> throw new IllegalArgumentException("Not a prize machine: " + mode.id());
        }
    }

    private static void requireSameLength(int[] multipliers, int[] weights) {
        if (weights.length != multipliers.length) throw new IllegalArgumentException("Every prize needs a weight");
    }

    /** How many balls of 2^rows fall into each pocket: the chance of a pocket, times 256. */
    private static int[] binomial() {
        int[] weights = new int[PACHINKO_POCKETS];
        weights[0] = 1;
        for (int pocket = 1; pocket < PACHINKO_POCKETS; pocket++) {
            weights[pocket] = weights[pocket - 1] * (PACHINKO_ROWS - pocket + 1) / pocket;
        }
        return weights;
    }

    @Override public int[] multipliers() { return multipliers.clone(); }
    @Override public int[] weights() { return weights.clone(); }

    /** The choices a player makes before playing: claw positions or targets; one for the others. */
    public int choices() {
        return mode == GameMode.PRIZE_CLAW || mode == GameMode.HIGH_STAKES ? multipliers.length : 1;
    }

    /** What one play with this choice can pay, each prize with its weight. */
    public List<Prize> odds(int choice) {
        requireChoice(choice);
        List<Prize> odds = new ArrayList<>();
        switch (mode) {
            case PRIZE_CLAW -> {
                odds.add(new Prize(0, GRIP_SCALE - weights[choice]));
                odds.add(new Prize(multipliers[choice], weights[choice]));
            }
            case HIGH_STAKES -> {
                odds.add(new Prize(0, multipliers[choice] - 100));
                odds.add(new Prize(multipliers[choice], 100));
            }
            default -> {
                for (int index = 0; index < multipliers.length; index++) odds.add(new Prize(multipliers[index], weights[index]));
            }
        }
        return odds;
    }

    /**
     * Plays once. {@code draw} answers a number in [0, bound), as {@link java.util.Random#nextInt(int)}.
     * The machines shows:
     * <ul>
     *   <li>wheel, pusher, gacha: the prize index, then where in its sector the wheel stops, in thousandths;</li>
     *   <li>claw: the position, 1 when the claw held the prize, 0 when it slipped;</li>
     *   <li>pachinko: the pocket, then the path, bit {@code r} set when the ball went right at row r;</li>
     *   <li>high stakes: the target index, then where the needle stopped among the target's hundredths:
     *   below 100 is the winning arc.</li>
     * </ul>
     */
    public Outcome play(int choice, IntUnaryOperator draw) {
        requireChoice(choice);
        switch (mode) {
            case PRIZE_CLAW -> {
                boolean held = draw(draw, GRIP_SCALE) < weights[choice];
                return new Outcome(held ? multipliers[choice] : 0, new int[] {choice, held ? 1 : 0});
            }
            case PACHINKO -> {
                int path = 0, pocket = 0;
                for (int row = 0; row < PACHINKO_ROWS; row++) {
                    if (draw(draw, 2) == 1) {
                        path |= 1 << row;
                        pocket++;
                    }
                }
                return new Outcome(multipliers[pocket], new int[] {pocket, path});
            }
            case HIGH_STAKES -> {
                int needle = draw(draw, multipliers[choice]);
                return new Outcome(needle < 100 ? multipliers[choice] : 0, new int[] {choice, needle});
            }
            default -> {
                long total = 0;
                for (int weight : weights) total += weight;
                int ticket = draw(draw, (int) total);
                int index = 0;
                for (long seen = weights[0]; ticket >= seen; seen += weights[++index]) {}
                return new Outcome(multipliers[index], new int[] {index, draw(draw, 1000)});
            }
        }
    }

    private static int draw(IntUnaryOperator draw, int bound) {
        int value = draw.applyAsInt(bound);
        if (value < 0 || value >= bound) throw new IllegalArgumentException("Draw outside [0, " + bound + "): " + value);
        return value;
    }

    private void requireChoice(int choice) {
        if (choice < 0 || choice >= choices()) throw new IllegalArgumentException("Unknown choice " + choice + " of " + mode.id());
    }

    /** The share of the stake one play with this choice gives back on average, before the return rate. */
    public BigDecimal returnToPlayer(int choice) {
        long paid = 0, total = 0;
        for (Prize prize : odds(choice)) {
            paid += (long) prize.multiplier() * prize.weight();
            total += prize.weight();
        }
        if (total == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(paid).divide(BigDecimal.valueOf(100 * total), 6, RoundingMode.HALF_UP);
    }

    /** The exact chance of a prize of {@link #odds}, as a fraction of one. */
    public BigDecimal chance(int choice, int prize) {
        List<Prize> odds = odds(choice);
        long total = 0;
        for (Prize entry : odds) total += entry.weight();
        if (total == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(odds.get(prize).weight()).divide(BigDecimal.valueOf(total), 8, RoundingMode.HALF_UP);
    }

    /** The largest prize any choice could pay, so a machine can refuse a bet it could not hand over. */
    public int bestMultiplier() {
        int best = 0;
        for (int index = 0; index < multipliers.length; index++) {
            boolean reachable = mode == GameMode.PACHINKO || mode == GameMode.HIGH_STAKES || weights[index] > 0;
            if (reachable) best = Math.max(best, multipliers[index]);
        }
        return best;
    }

    /** Everything a play pays, stake included, rounded down as announced. */
    public static long payout(long stake, int multiplier, BigDecimal returnRate) {
        if (stake <= 0 || multiplier < 0) throw new IllegalArgumentException("Invalid machine settlement");
        if (returnRate == null || returnRate.signum() <= 0 || returnRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Invalid return rate");
        }
        if (multiplier == 0) return 0;
        return BigDecimal.valueOf(stake).multiply(BigDecimal.valueOf(multiplier)).multiply(returnRate)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).longValueExact();
    }

    @Override public boolean equals(Object other) {
        return other instanceof MachineTable table && mode == table.mode
                && Arrays.equals(multipliers, table.multipliers) && Arrays.equals(weights, table.weights);
    }

    @Override public int hashCode() { return 31 * (31 * mode.hashCode() + Arrays.hashCode(multipliers)) + Arrays.hashCode(weights); }

    @Override public String toString() {
        return "MachineTable[" + mode.id() + ", " + Arrays.toString(multipliers) + ", " + Arrays.toString(weights) + "]";
    }
}
