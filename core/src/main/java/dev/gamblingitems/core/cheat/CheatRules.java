package dev.gamblingitems.core.cheat;

import java.util.Objects;
import java.util.function.IntToLongFunction;

/**
 * Pure rules of cheating at the casino: how likely a trick is to be spotted, how suspicion builds
 * up and fades, and what each trick changes in a game. The tools, the cameras and the punishments
 * themselves belong to the server integration.
 *
 * <p>Chances are counted in basis points, ten thousand being certain. A player's heat is a number
 * from zero to {@link #MAX_HEAT}: every trick that goes unnoticed warms it up, and time cools it down.
 */
public final class CheatRules {
    public static final int CERTAIN = 10_000;
    public static final int MAX_HEAT = 100;

    private CheatRules() {}

    /** Every trick of the casino, one per game it is used at. Identifiers are stored, never ordinals. */
    public enum Tool {
        /** Blackjack: a lens that reads the marked back of the hole card and of the next card of the shoe. */
        MARKED_LENS("marked_lens", 1_500, 15),
        /** Roulette: a magnet under the felt that drags the ball one pocket aside. */
        ROULETTE_MAGNET("roulette_magnet", 2_500, 25),
        /** Slot machine: a shim that stops a reel late, so the better of two lines is kept. */
        REEL_SHIM("reel_shim", 1_000, 10),
        /** Crash: a watch that vibrates just before the round crashes. */
        CRASH_WATCH("crash_watch", 2_000, 20);

        private final String id;
        private final int baseChance;
        private final int heat;

        Tool(String id, int baseChance, int heat) {
            this.id = id;
            this.baseChance = baseChance;
            this.heat = heat;
        }

        public String id() { return id; }
        /** How likely this trick is to be spotted by a cold player with no camera around, by default. */
        public int defaultBaseChance() { return baseChance; }
        /** How much suspicion this trick adds when it goes unnoticed, by default. */
        public int defaultHeat() { return heat; }

        public static Tool fromId(String id) {
            for (Tool tool : values()) if (tool.id.equals(id)) return tool;
            throw new IllegalArgumentException("Unknown cheat tool: " + id);
        }
    }

    /**
     * The tuning of the security of a server.
     *
     * @param heatChance        basis points added to every detection per point of heat
     * @param cameraChance      basis points added per security camera watching the player
     * @param maximumChance     detection never goes above this, so a trick is never hopeless
     * @param coolingMillis     one point of heat fades every this many milliseconds
     * @param banMillis         how long a caught cheater is kept out of the casino
     * @param caughtHeat        the heat a caught cheater is left with
     */
    public record Settings(int heatChance, int cameraChance, int maximumChance, long coolingMillis,
                           long banMillis, int caughtHeat) {
        public Settings {
            if (heatChance < 0 || cameraChance < 0 || maximumChance < 0 || maximumChance > CERTAIN
                    || coolingMillis < 1 || banMillis < 0 || caughtHeat < 0 || caughtHeat > MAX_HEAT) {
                throw new IllegalArgumentException("Invalid casino security settings");
            }
        }

        public static Settings defaults() {
            return new Settings(60, 1_200, 9_000, 30_000, 10 * 60_000L, 80);
        }
    }

    /**
     * How likely one use of a trick is to be spotted: the trick itself, the suspicion already weighing
     * on the player and every camera in sight, capped by the settings.
     */
    public static int detectionChance(Settings settings, int baseChance, int heat, int cameras) {
        Objects.requireNonNull(settings, "settings");
        if (baseChance < 0 || heat < 0 || cameras < 0) throw new IllegalArgumentException("Negative detection input");
        long chance = (long) baseChance + (long) Math.min(heat, MAX_HEAT) * settings.heatChance()
                + (long) cameras * settings.cameraChance();
        return (int) Math.min(settings.maximumChance(), Math.min(CERTAIN, chance));
    }

    /** True when a trick is spotted. {@code draw} is an independent server draw in [0, 10000). */
    public static boolean spotted(int chance, int draw) {
        if (draw < 0 || draw >= CERTAIN) throw new IllegalArgumentException("draw outside [0, 10000)");
        return draw < chance;
    }

    /** The heat left after some time without a trick: one point fades per cooling period. */
    public static int cooled(Settings settings, int heat, long elapsedMillis) {
        if (heat <= 0 || elapsedMillis <= 0) return Math.max(0, heat);
        long faded = elapsedMillis / settings.coolingMillis();
        return (int) Math.max(0, heat - Math.min(heat, faded));
    }

    /** The heat after a trick that nobody noticed. */
    public static int warmed(int heat, int added) {
        return Math.min(MAX_HEAT, Math.max(0, heat) + Math.max(0, added));
    }

    /**
     * Where a magnet leaves the ball: the drawn number, or one of its two neighbours on the wheel
     * when that pays this player more. A magnet only ever drags the ball one pocket aside.
     *
     * @param pocket    position of the drawn number around the wheel
     * @param pockets   how many pockets the wheel has
     * @param numberAt  the number printed in a pocket
     * @param payout    what this player would receive if a number came out
     * @return the number the ball finally rests on
     */
    public static int magnet(int pocket, int pockets, java.util.function.IntUnaryOperator numberAt,
                             IntToLongFunction payout) {
        if (pockets < 3 || pocket < 0 || pocket >= pockets) throw new IllegalArgumentException("Invalid wheel");
        int drawn = numberAt.applyAsInt(pocket);
        int best = drawn;
        long bestPayout = payout.applyAsLong(drawn);
        for (int step : new int[] {-1, 1}) {
            int neighbour = numberAt.applyAsInt(Math.floorMod(pocket + step, pockets));
            long paid = payout.applyAsLong(neighbour);
            if (paid > bestPayout) {
                best = neighbour;
                bestPayout = paid;
            }
        }
        return best;
    }

    /**
     * True when a watch should vibrate: the flight would reach its secret crash point within
     * {@code leadTicks}, judging from the multiplier the round will show at that tick.
     */
    public static boolean watchVibrates(int multiplierAhead, int crashPoint) {
        return multiplierAhead >= crashPoint;
    }
}
