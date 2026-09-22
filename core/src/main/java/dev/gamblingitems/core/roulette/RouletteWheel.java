package dev.gamblingitems.core.roulette;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A real roulette wheel and its table: the numbers 0 to 36, their colours, and every bet a player
 * can place on them. The spin, the phases and the payments belong to the server integration.
 *
 * <p>Payouts include the stake, as a table announces them. On this wheel every bet returns the
 * same 36/37 of what it stakes, which is what makes the table fair to read: no bet is a trap.
 */
public final class RouletteWheel {
    public static final int POCKETS = 37;
    public static final int ZERO = 0;

    /** The order of the pockets around a European wheel, starting at zero and turning clockwise. */
    private static final int[] ORDER = {
            0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
            5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
    private static final boolean[] RED = new boolean[POCKETS];

    static {
        for (int number : new int[] {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}) {
            RED[number] = true;
        }
    }

    private RouletteWheel() {}

    public enum Colour { RED, BLACK, GREEN }

    /** Every bet of the table, with what it pays, stake included. */
    public enum BetType {
        STRAIGHT("straight", 36),
        RED("red", 2),
        BLACK("black", 2),
        EVEN("even", 2),
        ODD("odd", 2),
        LOW("low", 2),
        HIGH("high", 2),
        DOZEN("dozen", 3),
        COLUMN("column", 3);

        private final String id;
        private final int payout;

        BetType(String id, int payout) {
            this.id = id;
            this.payout = payout;
        }

        public String id() { return id; }
        /** What one staked unit becomes when the bet wins, stake included. */
        public int payout() { return payout; }
        /** True when this bet needs a selection: a number, a dozen or a column. */
        public boolean needsChoice() { return this == STRAIGHT || this == DOZEN || this == COLUMN; }

        public int choices() {
            return switch (this) {
                case STRAIGHT -> POCKETS;
                case DOZEN, COLUMN -> 3;
                default -> 1;
            };
        }

        public static BetType fromId(String id) {
            for (BetType type : values()) if (type.id.equals(id)) return type;
            throw new IllegalArgumentException("Unknown roulette bet: " + id);
        }
    }

    /** One bet of the table: its kind and, when it needs one, which number, dozen or column. */
    public record Bet(BetType type, int choice) {
        public Bet {
            if (type == null || choice < 0 || choice >= type.choices()) {
                throw new IllegalArgumentException("Invalid roulette bet");
            }
        }

        public boolean wins(int number) { return RouletteWheel.wins(this, number); }
        public int payout() { return type.payout(); }
    }

    public static Colour colourOf(int number) {
        requireNumber(number);
        if (number == ZERO) return Colour.GREEN;
        return RED[number] ? Colour.RED : Colour.BLACK;
    }

    /** Position of a number around the wheel, used to draw it and to place the ball. */
    public static int pocketOf(int number) {
        requireNumber(number);
        for (int index = 0; index < ORDER.length; index++) {
            if (ORDER[index] == number) return index;
        }
        throw new IllegalStateException("A number of the wheel is missing from its order");
    }

    public static int numberAtPocket(int pocket) {
        if (pocket < 0 || pocket >= POCKETS) throw new IllegalArgumentException("Pocket outside the wheel");
        return ORDER[pocket];
    }

    /** The caller supplies an independent server-side draw in [0, bound). */
    public static int spin(long draw, long bound) {
        if (bound < 1 || draw < 0 || draw >= bound) throw new IllegalArgumentException("draw is outside the wheel");
        return ORDER[(int) (draw * POCKETS / bound)];
    }

    public static boolean wins(Bet bet, int number) {
        requireNumber(number);
        if (number == ZERO) return bet.type() == BetType.STRAIGHT && bet.choice() == ZERO;
        return switch (bet.type()) {
            case STRAIGHT -> bet.choice() == number;
            case RED -> colourOf(number) == Colour.RED;
            case BLACK -> colourOf(number) == Colour.BLACK;
            case EVEN -> number % 2 == 0;
            case ODD -> number % 2 == 1;
            case LOW -> number <= 18;
            case HIGH -> number >= 19;
            case DOZEN -> (number - 1) / 12 == bet.choice();
            case COLUMN -> (number - 1) % 3 == bet.choice();
        };
    }

    /** Everything the player receives, stake included; zero when the bet did not come out. */
    public static long payout(long stake, Bet bet, int number) {
        if (stake <= 0) throw new IllegalArgumentException("A bet needs a positive stake");
        return wins(bet, number) ? Math.multiplyExact(stake, bet.payout()) : 0;
    }

    /** How many pockets this bet covers, which is what its chance is made of. */
    public static int pocketsCovered(Bet bet) {
        int covered = 0;
        for (int number = 0; number < POCKETS; number++) {
            if (wins(bet, number)) covered++;
        }
        return covered;
    }

    public static BigDecimal chanceOf(Bet bet) {
        return BigDecimal.valueOf(pocketsCovered(bet) * 100L)
                .divide(BigDecimal.valueOf(POCKETS), 4, RoundingMode.HALF_UP);
    }

    /** What a bet returns on average. Shown, never silently corrected. */
    public static BigDecimal returnRate(Bet bet) {
        return BigDecimal.valueOf((long) pocketsCovered(bet) * bet.payout())
                .divide(BigDecimal.valueOf(POCKETS), 6, RoundingMode.HALF_UP);
    }

    private static void requireNumber(int number) {
        if (number < 0 || number >= POCKETS) throw new IllegalArgumentException("Number outside the wheel");
    }
}
