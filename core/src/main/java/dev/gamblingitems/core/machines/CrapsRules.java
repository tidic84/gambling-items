package dev.gamblingitems.core.machines;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Craps on the pass and don't pass lines, as printed on every craps layout. The come-out roll wins the
 * pass line on 7 or 11 and loses it on 2, 3 or 12; any other total becomes the point, and the dice are
 * rolled until the point comes back (pass wins) or a 7 does (pass loses). The don't pass line takes
 * the other side, except that a 12 on the come-out is barred: the bet is given back.
 *
 * <p>The machine draws the event with its exact chance, then shows dice that lead to it.
 */
public final class CrapsRules {
    /** What a round ends with, seen from the pass line. */
    public static final int PASS_WINS = 0, PASS_LOSES = 1, BAR_TWELVE = 2;
    /**
     * The exact chance of each event, out of 1980 (36 times 55): the pass line wins 244 times in 495,
     * a 12 comes out once in 36 rolls, and the pass line loses every other round.
     */
    public static final int[] EVENTS = {976, 949, 55};
    /** The longest round shown; a longer one is drawn again, which only shortens what is shown. */
    private static final int MOST_ROLLS = 24;

    private CrapsRules() {}

    /** The event a round of rolls ends with; each roll is the two dice, {@code die1 * 10 + die2}. */
    public static int event(List<Integer> rolls) {
        int comeOut = total(rolls.get(0));
        if (comeOut == 7 || comeOut == 11) return PASS_WINS;
        if (comeOut == 12) return BAR_TWELVE;
        if (comeOut == 2 || comeOut == 3) return PASS_LOSES;
        int last = total(rolls.get(rolls.size() - 1));
        return last == comeOut ? PASS_WINS : PASS_LOSES;
    }

    public static int total(int roll) { return roll / 10 + roll % 10; }

    /** The point a round is played for, or 0 when the come-out roll settled it. */
    public static int point(List<Integer> rolls) {
        int comeOut = total(rolls.get(0));
        return comeOut == 2 || comeOut == 3 || comeOut == 7 || comeOut == 11 || comeOut == 12 ? 0 : comeOut;
    }

    /** A round of fair rolls that ends with {@code event}, the same for every viewer of the same seed. */
    public static List<Integer> rolls(int event, int seed) {
        if (event < 0 || event >= EVENTS.length) throw new IllegalArgumentException("Unknown craps event " + event);
        SplittableRandom random = new SplittableRandom(seed);
        while (true) {
            List<Integer> rolls = round(random);
            if (rolls != null && event(rolls) == event) return rolls;
        }
    }

    private static List<Integer> round(SplittableRandom random) {
        List<Integer> rolls = new ArrayList<>();
        rolls.add(roll(random));
        int point = point(rolls);
        if (point == 0) return rolls;
        while (rolls.size() < MOST_ROLLS) {
            int roll = roll(random);
            rolls.add(roll);
            if (total(roll) == point || total(roll) == 7) return rolls;
        }
        return null;
    }

    private static int roll(SplittableRandom random) { return (random.nextInt(6) + 1) * 10 + random.nextInt(6) + 1; }
}
