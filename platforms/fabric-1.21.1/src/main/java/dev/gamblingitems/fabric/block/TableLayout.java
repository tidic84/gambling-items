package dev.gamblingitems.fabric.block;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.roulette.RouletteWheel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Shared geometry of a table, for the renderer that draws its felt and for the server that reads
 * a click on it. Coordinates are the pixels of a block face, 128 to a block, counted from the
 * middle of the block a player stands at: the felt runs from -192 to 192 across, and from -128
 * at the far edge to 128 at the edge the player leans on.
 *
 * <p>A table is played on directly: the things standing along the near edge answer a click, and
 * on a roulette every area of the layout is a bet of its own. Nothing here decides anything; it only
 * says where things are, so what is drawn and what answers a click can never drift apart.
 */
public final class TableLayout {
    /** The surface: three blocks across, two deep, the anchor block at the front middle. */
    public static final float HALF_WIDTH = 190, HALF_DEPTH = 126;
    /** The printed layout of a roulette: the zero, twelve columns of three numbers, then the column bets. */
    public static final float LAYOUT_LEFT = -50, LAYOUT_TOP = -50, ZERO_WIDTH = 26;
    public static final float CELL_WIDTH = 15, CELL_HEIGHT = 30, COLUMN_WIDTH = 22;
    /** The dozens, then the even money areas, printed underneath the numbers as on a real layout. */
    public static final float DOZEN_TOP = LAYOUT_TOP + CELL_HEIGHT * 3, DOZEN_HEIGHT = 20;
    public static final float OUTSIDE_TOP = DOZEN_TOP + DOZEN_HEIGHT, OUTSIDE_HEIGHT = 22;
    public static final float OUTSIDE_WIDTH = CELL_WIDTH * 2;
    /** Where the items staked on this table are laid out, one game at a time. */
    public static final float STAKE_Y = 62;

    private TableLayout() {}

    /**
     * The controls of a table: the moves of the game, then the four things every game shares. They
     * are the same actions as the panel of a station, read from the same names, but each one is a
     * thing standing on the table rather than a printed button: the drop box the stake goes into,
     * the player's chip tray, the payout cup, the sign giving the table's rules, and on a blackjack
     * or a bingo the domed buttons set in the rail. A roulette is left as a real one is: every bet
     * is a printed area of its layout, and the croupier's float is the only thing that answers.
     */
    public static List<StationPanel.Control> controls(GameMode mode, int phase) {
        List<StationPanel.Control> controls = new ArrayList<>();
        if (mode == GameMode.ROULETTE) {
            // Only what a real roulette carries: the croupier's float, on the far side beside the
            // wheel, pays the winnings out. A stake goes straight from the hand onto the layout,
            // and a click on the bare cloth opens the window for everything else.
            add(controls, mode, phase, StationPanel.COLLECT, -44, -116, 128, 54);
            return controls;
        }
        add(controls, mode, phase, StationPanel.DEPOSIT, -186, 78, 38, 38);
        add(controls, mode, phase, StationPanel.RETURN, -144, 84, 62, 30);
        for (int move = 0; move < 3; move++) {
            add(controls, mode, phase, StationPanel.FIRST_MOVE + move, -62 + move * 44, 86, 36, 30);
        }
        add(controls, mode, phase, StationPanel.COLLECT, 92, 78, 48, 38);
        add(controls, mode, phase, StationPanel.OPEN, 148, 56, 40, 60);
        return controls;
    }

    private static void add(List<StationPanel.Control> controls, GameMode mode, int phase, int action,
                            int x, int y, int width, int height) {
        // A move is named for the phase the table is in; the shared tools always read the same.
        String label = action >= StationPanel.FIRST_MOVE && action <= StationPanel.FIRST_MOVE + 2
                ? StationPanel.move(mode, action, phase) : StationPanel.label(mode, action);
        controls.add(new StationPanel.Control(action, x, y, width, height, label));
    }

    /** True when this control lies on the felt, where a hand can reach it. */
    public static boolean reachable(StationPanel.Control control) {
        return control.x() >= -HALF_WIDTH && control.y() >= -HALF_DEPTH
                && control.x() + control.width() <= HALF_WIDTH
                && control.y() + control.height() <= HALF_DEPTH;
    }

    /**
     * Where a click landed on the felt, or null when it did not land on the top of the table.
     * The point is given in felt pixels, counted from the middle of the anchor block.
     */
    public static float[] felt(Direction facing, BlockHitResult hit, BlockPos anchor) {
        if (hit.getDirection() != Direction.UP) return null;
        var local = hit.getLocation().subtract(anchor.getX(), anchor.getY(), anchor.getZ());
        double across = switch (facing) {
            case NORTH -> 1 - local.x;
            case SOUTH -> local.x;
            case WEST -> local.z;
            case EAST -> 1 - local.z;
            default -> Double.NaN;
        };
        double depth = switch (facing) {
            case NORTH -> 1 - local.z;
            case SOUTH -> local.z;
            case WEST -> 1 - local.x;
            case EAST -> local.x;
            default -> Double.NaN;
        };
        if (Double.isNaN(across) || Double.isNaN(depth)) return null;
        return new float[] {(float) ((across - 0.5) * 128), (float) (depth * 128)};
    }

    /** The control a click on the felt presses, or -1 when it pressed none of them. */
    public static int button(GameMode mode, Direction facing, BlockHitResult hit, BlockPos anchor) {
        float[] point = felt(facing, hit, anchor);
        if (point == null) return -1;
        for (StationPanel.Control control : controls(mode, 0)) {
            if (point[0] >= control.x() && point[0] < control.x() + control.width()
                    && point[1] >= control.y() && point[1] < control.y() + control.height()) {
                return control.action();
            }
        }
        return -1;
    }

    /** Where one printed number of the roulette layout sits on the felt. */
    public static float[] numberBox(int number) {
        if (number < 0 || number > RouletteWheel.POCKETS - 1) {
            throw new IllegalArgumentException("No such number on a layout: " + number);
        }
        if (number == 0) {
            return new float[] {LAYOUT_LEFT, LAYOUT_TOP, ZERO_WIDTH - 1, CELL_HEIGHT * 3 - 1};
        }
        int index = (number - 1) / 3;
        int line = 3 - (number - index * 3);
        return new float[] {LAYOUT_LEFT + ZERO_WIDTH + index * CELL_WIDTH, LAYOUT_TOP + line * CELL_HEIGHT,
                CELL_WIDTH - 1, CELL_HEIGHT - 1};
    }

    /** Where one "2 to 1" box sits, at the end of its row: 0 is the row of 1, 4, 7, nearest the player. */
    public static float[] columnBox(int column) {
        if (column < 0 || column >= 3) throw new IllegalArgumentException("No such column: " + column);
        return new float[] {LAYOUT_LEFT + ZERO_WIDTH + 12 * CELL_WIDTH, LAYOUT_TOP + (2 - column) * CELL_HEIGHT,
                COLUMN_WIDTH - 1, CELL_HEIGHT - 1};
    }

    /** Where one dozen sits, under the four columns of numbers it covers. */
    public static float[] dozenBox(int dozen) {
        if (dozen < 0 || dozen >= 3) throw new IllegalArgumentException("No such dozen: " + dozen);
        return new float[] {LAYOUT_LEFT + ZERO_WIDTH + dozen * 4 * CELL_WIDTH, DOZEN_TOP,
                4 * CELL_WIDTH - 1, DOZEN_HEIGHT - 1};
    }

    /** Where one even money area sits, in the order they are printed. */
    public static float[] outsideBox(int index) {
        if (index < 0 || index >= 6) throw new IllegalArgumentException("No such area: " + index);
        return new float[] {LAYOUT_LEFT + ZERO_WIDTH + index * OUTSIDE_WIDTH, OUTSIDE_TOP, OUTSIDE_WIDTH - 1,
                OUTSIDE_HEIGHT};
    }

    /** The even money areas, in the order they are printed along the felt. */
    public static RouletteWheel.Bet outsideBet(int index) {
        return switch (index) {
            case 0 -> new RouletteWheel.Bet(RouletteWheel.BetType.LOW, 0);
            case 1 -> new RouletteWheel.Bet(RouletteWheel.BetType.EVEN, 0);
            case 2 -> new RouletteWheel.Bet(RouletteWheel.BetType.RED, 0);
            case 3 -> new RouletteWheel.Bet(RouletteWheel.BetType.BLACK, 0);
            case 4 -> new RouletteWheel.Bet(RouletteWheel.BetType.ODD, 0);
            default -> new RouletteWheel.Bet(RouletteWheel.BetType.HIGH, 0);
        };
    }

    /** Where the area of a bet is printed, or null for a bet the layout does not print. */
    public static float[] boxOf(RouletteWheel.Bet bet) {
        return switch (bet.type()) {
            case STRAIGHT -> numberBox(bet.choice());
            case DOZEN -> dozenBox(bet.choice());
            case COLUMN -> columnBox(bet.choice());
            default -> {
                for (int index = 0; index < 6; index++) {
                    if (outsideBet(index).equals(bet)) yield outsideBox(index);
                }
                yield null;
            }
        };
    }

    /** The bet printed where a click landed on a roulette felt, or null if it landed elsewhere. */
    public static RouletteWheel.Bet betAt(float x, float y) {
        for (int number = 0; number < RouletteWheel.POCKETS; number++) {
            if (inside(numberBox(number), x, y)) {
                return new RouletteWheel.Bet(RouletteWheel.BetType.STRAIGHT, number);
            }
        }
        for (int index = 0; index < 3; index++) {
            if (inside(columnBox(index), x, y)) return new RouletteWheel.Bet(RouletteWheel.BetType.COLUMN, index);
            if (inside(dozenBox(index), x, y)) return new RouletteWheel.Bet(RouletteWheel.BetType.DOZEN, index);
        }
        for (int index = 0; index < 6; index++) {
            if (inside(outsideBox(index), x, y)) return outsideBet(index);
        }
        return null;
    }

    private static boolean inside(float[] box, float x, float y) {
        return x >= box[0] && x < box[0] + box[2] && y >= box[1] && y < box[1] + box[3];
    }
}
