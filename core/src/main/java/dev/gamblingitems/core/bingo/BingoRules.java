package dev.gamblingitems.core.bingo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

/**
 * Pure rules of a bingo round: how a card is made, what a draw marks, and when a card wins.
 * The draws themselves, the seats and the payments belong to the server integration.
 *
 * <p>The cards are the ones players know: five columns of five, each column taking its numbers
 * from its own range, and a free square in the middle. A card wins on a full line, column or
 * diagonal; the pot is then shared between the cards that completed one on the same number.
 */
public final class BingoRules {
    public static final int COLUMNS = 5, ROWS = 5, SIZE = COLUMNS * ROWS;
    /** Numbers run from one to seventy five, fifteen for each column. */
    public static final int PER_COLUMN = 15;
    public static final int NUMBERS = COLUMNS * PER_COLUMN;
    /** The middle square is free, and counts as marked from the start. */
    public static final int FREE_SQUARE = SIZE / 2;

    private BingoRules() {}

    /** The column a number belongs to: one for B, two for I, and so on. */
    public static int columnOf(int number) {
        requireNumber(number);
        return (number - 1) / PER_COLUMN;
    }

    /**
     * A card of twenty five squares, column by column, with a free middle.
     * The caller supplies the draws, so the same draws always build the same card.
     */
    public static int[] card(IntUnaryOperator draw) {
        Objects.requireNonNull(draw, "draw");
        int[] card = new int[SIZE];
        for (int column = 0; column < COLUMNS; column++) {
            List<Integer> pool = new ArrayList<>(PER_COLUMN);
            for (int index = 0; index < PER_COLUMN; index++) pool.add(column * PER_COLUMN + index + 1);
            for (int row = 0; row < ROWS; row++) {
                int pick = draw.applyAsInt(pool.size());
                if (pick < 0 || pick >= pool.size()) throw new IllegalArgumentException("A draw left the pool");
                card[row * COLUMNS + column] = pool.remove(pick);
            }
        }
        card[FREE_SQUARE] = 0;
        return card;
    }

    /** True when every square of the card holds a number of its own column, and only one free. */
    public static boolean isCard(int[] card) {
        if (card == null || card.length != SIZE || card[FREE_SQUARE] != 0) return false;
        boolean[] seen = new boolean[NUMBERS + 1];
        for (int square = 0; square < SIZE; square++) {
            if (square == FREE_SQUARE) continue;
            int number = card[square];
            if (number < 1 || number > NUMBERS || seen[number]) return false;
            if (columnOf(number) != square % COLUMNS) return false;
            seen[number] = true;
        }
        return true;
    }

    /** The squares a card has marked, given everything drawn so far. The free square is always marked. */
    public static boolean[] marks(int[] card, boolean[] drawn) {
        boolean[] marks = new boolean[SIZE];
        for (int square = 0; square < SIZE; square++) {
            marks[square] = square == FREE_SQUARE
                    || (card[square] >= 1 && card[square] <= NUMBERS && drawn[card[square]]);
        }
        return marks;
    }

    /** True when a full row, column or diagonal is marked. */
    public static boolean hasLine(boolean[] marks) {
        for (int row = 0; row < ROWS; row++) {
            boolean full = true;
            for (int column = 0; column < COLUMNS; column++) full &= marks[row * COLUMNS + column];
            if (full) return true;
        }
        for (int column = 0; column < COLUMNS; column++) {
            boolean full = true;
            for (int row = 0; row < ROWS; row++) full &= marks[row * COLUMNS + column];
            if (full) return true;
        }
        boolean down = true, up = true;
        for (int step = 0; step < ROWS; step++) {
            down &= marks[step * COLUMNS + step];
            up &= marks[step * COLUMNS + (COLUMNS - 1 - step)];
        }
        return down || up;
    }

    /** How many squares of the card are still missing for its best line. */
    public static int missing(boolean[] marks) {
        int best = COLUMNS;
        for (int row = 0; row < ROWS; row++) {
            int left = 0;
            for (int column = 0; column < COLUMNS; column++) if (!marks[row * COLUMNS + column]) left++;
            best = Math.min(best, left);
        }
        for (int column = 0; column < COLUMNS; column++) {
            int left = 0;
            for (int row = 0; row < ROWS; row++) if (!marks[row * COLUMNS + column]) left++;
            best = Math.min(best, left);
        }
        int down = 0, up = 0;
        for (int step = 0; step < ROWS; step++) {
            if (!marks[step * COLUMNS + step]) down++;
            if (!marks[step * COLUMNS + (COLUMNS - 1 - step)]) up++;
        }
        return Math.min(best, Math.min(down, up));
    }

    /**
     * What one winning card takes: the pot, less the share the table keeps, split between the
     * cards that filled a line on the same number. Rounded down, as every payout of this mod is.
     */
    public static long share(long pot, int winners, BigDecimal returnRate) {
        if (pot <= 0 || winners < 1) throw new IllegalArgumentException("A bingo pays a pot to a winner");
        Objects.requireNonNull(returnRate, "returnRate");
        if (returnRate.signum() <= 0 || returnRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Invalid return rate");
        }
        return BigDecimal.valueOf(pot).multiply(returnRate)
                .divide(BigDecimal.valueOf(winners), 0, RoundingMode.FLOOR).longValueExact();
    }

    private static void requireNumber(int number) {
        if (number < 1 || number > NUMBERS) throw new IllegalArgumentException("Number outside the drum");
    }
}
