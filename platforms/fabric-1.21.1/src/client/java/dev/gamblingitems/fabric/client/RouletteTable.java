package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.roulette.RouletteWheel;
import dev.gamblingitems.core.roulette.RouletteWheel.Bet;
import dev.gamblingitems.core.roulette.RouletteWheel.BetType;
import java.util.ArrayList;
import java.util.List;

/**
 * The felt of a real roulette table: where each area sits, so the same rectangle is drawn,
 * hovered and clicked. Coordinates are relative to the top left corner of the table.
 */
public final class RouletteTable {
    public static final int CELL_WIDTH = 20, CELL_HEIGHT = 19;
    public static final int ZERO_WIDTH = 20;
    public static final int WIDTH = ZERO_WIDTH + 12 * CELL_WIDTH + CELL_WIDTH;
    public static final int HEIGHT = 3 * CELL_HEIGHT + 2 * CELL_HEIGHT;

    private RouletteTable() {}

    /** One clickable area of the felt. */
    public record Area(Bet bet, int x, int y, int width, int height) {
        public boolean holds(int pointX, int pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }
        public int centreX() { return x + width / 2; }
        public int centreY() { return y + height / 2; }
    }

    /**
     * Every area, laid out as on a European table: zero on the left, the three rows of numbers,
     * the columns on the right, then the dozens and the even-money bets underneath.
     */
    public static List<Area> areas() {
        List<Area> areas = new ArrayList<>();
        areas.add(new Area(new Bet(BetType.STRAIGHT, 0), 0, 0, ZERO_WIDTH, 3 * CELL_HEIGHT));
        for (int column = 0; column < 12; column++) {
            for (int row = 0; row < 3; row++) {
                // The top row holds 3, 6, 9 …, as the felt prints them.
                int number = column * 3 + (3 - row);
                areas.add(new Area(new Bet(BetType.STRAIGHT, number),
                        ZERO_WIDTH + column * CELL_WIDTH, row * CELL_HEIGHT, CELL_WIDTH, CELL_HEIGHT));
            }
        }
        for (int row = 0; row < 3; row++) {
            areas.add(new Area(new Bet(BetType.COLUMN, 2 - row),
                    ZERO_WIDTH + 12 * CELL_WIDTH, row * CELL_HEIGHT, CELL_WIDTH, CELL_HEIGHT));
        }
        int dozenWidth = 4 * CELL_WIDTH;
        for (int dozen = 0; dozen < 3; dozen++) {
            areas.add(new Area(new Bet(BetType.DOZEN, dozen),
                    ZERO_WIDTH + dozen * dozenWidth, 3 * CELL_HEIGHT, dozenWidth, CELL_HEIGHT));
        }
        BetType[] outside = {BetType.LOW, BetType.EVEN, BetType.RED, BetType.BLACK, BetType.ODD, BetType.HIGH};
        int outsideWidth = 2 * CELL_WIDTH;
        for (int index = 0; index < outside.length; index++) {
            areas.add(new Area(new Bet(outside[index], 0),
                    ZERO_WIDTH + index * outsideWidth, 4 * CELL_HEIGHT, outsideWidth, CELL_HEIGHT));
        }
        return areas;
    }

    /** The area under a point, or null when the point is outside the felt. */
    public static Area at(List<Area> areas, int pointX, int pointY) {
        for (Area area : areas) {
            if (area.holds(pointX, pointY)) return area;
        }
        return null;
    }
}
