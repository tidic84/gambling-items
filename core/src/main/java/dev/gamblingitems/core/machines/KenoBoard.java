package dev.gamblingitems.core.machines;

import java.util.SplittableRandom;

/**
 * Keno, five spots: the player's five numbers of 1 to 80, against the twenty the house draws. The
 * paytable is read by the number of hits, from none to all five.
 *
 * <p>The machine draws the hits with their exact chance, then shows a board that has them.
 */
public final class KenoBoard {
    public static final int NUMBERS = 80, DRAWN = 20, SPOTS = 5;
    /**
     * The ways to hit 0 to 5 of five spots when twenty of eighty are drawn: C(20, h) C(60, 5 - h),
     * out of C(80, 5) = 24 040 016.
     */
    public static final int[] WEIGHTS = {5_461_512, 9_752_700, 6_501_800, 2_017_800, 290_700, 15_504};

    /** The five numbers played and the twenty drawn, in the order they came out. */
    public record Board(int[] spots, int[] drawn) {}

    private KenoBoard() {}

    /** A board where exactly {@code hits} of the spots are drawn, the same for every viewer of the same seed. */
    public static Board layout(int hits, int seed) {
        if (hits < 0 || hits > SPOTS) throw new IllegalArgumentException("Five spots hit 0 to 5 times, not " + hits);
        SplittableRandom random = new SplittableRandom(seed);
        int[] numbers = shuffled(NUMBERS, random);
        int[] drawn = new int[DRAWN];
        for (int index = 0; index < DRAWN; index++) drawn[index] = numbers[index] + 1;
        // The hits among the drawn numbers, the misses among the sixty left.
        int[] fromDrawn = shuffled(DRAWN, random), fromRest = shuffled(NUMBERS - DRAWN, random);
        int[] spots = new int[SPOTS];
        for (int index = 0; index < SPOTS; index++) {
            spots[index] = index < hits ? drawn[fromDrawn[index]] : numbers[DRAWN + fromRest[index - hits]] + 1;
        }
        java.util.Arrays.sort(spots);
        return new Board(spots, drawn);
    }

    private static int[] shuffled(int count, SplittableRandom random) {
        int[] values = new int[count];
        for (int index = 0; index < count; index++) values[index] = index;
        for (int index = count - 1; index > 0; index--) {
            int other = random.nextInt(index + 1), kept = values[index];
            values[index] = values[other];
            values[other] = kept;
        }
        return values;
    }
}
