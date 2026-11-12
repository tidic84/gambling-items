package dev.gamblingitems.core.machines;

import java.util.SplittableRandom;

/**
 * An instant scratch card of nine cells: three cells with the same prize win that prize. The machine
 * draws the prize from its table, then prints a card that shows it: the won prize three times and no
 * other prize more than twice; a losing card has no prize three times.
 */
public final class ScratchCard {
    public static final int CELLS = 9;
    /** A cell with a star instead of a prize, which never wins. */
    public static final int BLANK = -1;

    private ScratchCard() {}

    /**
     * The cells of the card, each the index of a prize of {@code multipliers} or {@link #BLANK}. The prize
     * {@code drawn} is printed three times when it pays; the other paying prizes fill the card as decoys.
     */
    public static int[] layout(int[] multipliers, int drawn, int seed) {
        if (drawn < 0 || drawn >= multipliers.length) throw new IllegalArgumentException("Unknown prize " + drawn);
        SplittableRandom random = new SplittableRandom(seed);
        int[] cells = new int[CELLS];
        int[] left = new int[multipliers.length];
        for (int prize = 0; prize < multipliers.length; prize++) left[prize] = multipliers[prize] > 0 ? 2 : 0;
        int filled = 0;
        if (multipliers[drawn] > 0) {
            left[drawn] = 0;
            for (; filled < 3; filled++) cells[filled] = drawn;
        }
        for (; filled < CELLS; filled++) {
            int options = 1;
            for (int count : left) options += count > 0 ? 1 : 0;
            int pick = random.nextInt(options), prize = BLANK;
            for (int index = 0; index < left.length && pick > 0; index++) {
                if (left[index] > 0 && --pick == 0) prize = index;
            }
            if (prize != BLANK) left[prize]--;
            cells[filled] = prize;
        }
        for (int index = CELLS - 1; index > 0; index--) {
            int other = random.nextInt(index + 1), kept = cells[index];
            cells[index] = cells[other];
            cells[other] = kept;
        }
        return cells;
    }

    /** The order the cells are scratched in, so every viewer sees the card uncovered alike. */
    public static int[] scratchOrder(int seed) {
        SplittableRandom random = new SplittableRandom(~seed);
        int[] order = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        for (int index = CELLS - 1; index > 0; index--) {
            int other = random.nextInt(index + 1), kept = order[index];
            order[index] = order[other];
            order[other] = kept;
        }
        return order;
    }
}
