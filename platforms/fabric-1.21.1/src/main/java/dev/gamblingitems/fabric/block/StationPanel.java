package dev.gamblingitems.fabric.block;

import dev.gamblingitems.core.GameMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Shared geometry for the world renderer and server-side hit testing. Coordinates are pixels,
 * with 128 of them to a block, counted from the middle of the face of the controller block.
 *
 * <p>A station is a monitor of six blocks, three wide and two high, and the controller is the
 * middle block of the bottom row. The controls live on that bottom row, where a player stands:
 * the three moves of the game on one line, and underneath the four things every game shares —
 * put chips in, take them back, take the winnings, open the full screen.
 *
 * <p>Every control has to sit inside {@link #reachable}, the surface the monitor actually occupies;
 * anything drawn past it could never be clicked.
 */
public final class StationPanel {
    /** The monitor, in panel pixels: three blocks wide, one block below the controller line. */
    public static final int MONITOR_LEFT = -192, MONITOR_RIGHT = 192, MONITOR_TOP = -192, MONITOR_BOTTOM = 64;
    /** The controls: a row of moves, then the row every game shares, on the bottom blocks. */
    public static final int LEFT = -190, TOP = -12, MOVE_WIDTH = 126, MOVE_HEIGHT = 30;
    public static final int TOOL_WIDTH = 93, TOOL_HEIGHT = 26, TOOL_TOP = TOP + MOVE_HEIGHT + 2;

    /** Actions, stable across layouts: the server reads these, never a position. */
    public static final int DEPOSIT = 0, RETURN = 2, FIRST_MOVE = 3, COLLECT = 6, OPEN = 8;

    private StationPanel() {}

    public record Control(int action, int x, int y, int width, int height, String label) {
        /** True when this control lies on the monitor, where a hand can reach it. */
        public boolean reachable() {
            return x >= MONITOR_LEFT && y >= MONITOR_TOP
                    && x + width <= MONITOR_RIGHT && y + height <= MONITOR_BOTTOM;
        }
    }

    public static List<Control> controls(GameMode mode, int phase) {
        List<Control> controls = new ArrayList<>();
        for (int move = 0; move < 3; move++) {
            controls.add(new Control(FIRST_MOVE + move, LEFT + move * (MOVE_WIDTH + 2), TOP,
                    MOVE_WIDTH, MOVE_HEIGHT, move(mode, FIRST_MOVE + move, phase)));
        }
        int[] tools = {DEPOSIT, RETURN, COLLECT, OPEN};
        for (int index = 0; index < tools.length; index++) {
            controls.add(new Control(tools[index], LEFT + index * (TOOL_WIDTH + 2), TOOL_TOP,
                    TOOL_WIDTH, TOOL_HEIGHT, label(mode, tools[index])));
        }
        return controls;
    }

    public static int button(GameMode mode, Direction facing, BlockHitResult hit, net.minecraft.core.BlockPos anchor) {
        if (hit.getDirection() != facing) return -1;
        var local = hit.getLocation().subtract(anchor.getX(), anchor.getY(), anchor.getZ());
        double horizontal = switch (facing) {
            case NORTH -> 1 - local.x;
            case SOUTH -> local.x;
            case WEST -> local.z;
            case EAST -> 1 - local.z;
            default -> -100;
        };
        double x = (horizontal - 0.5) * 128;
        double y = (0.5 - local.y) * 128;
        for (Control control : controls(mode, 0)) {
            if (x >= control.x && x < control.x + control.width
                    && y >= control.y && y < control.y + control.height) return control.action;
        }
        return -1;
    }

    public static int button(Direction facing, BlockHitResult hit) {
        return button(facing, hit, hit.getBlockPos());
    }

    public static int button(Direction facing, BlockHitResult hit, net.minecraft.core.BlockPos anchor) {
        return button(GameMode.ROULETTE, facing, hit, anchor);
    }

    /** The three moves of a game, named as that game names them. */
    public static String move(GameMode mode, int button, int phase) {
        int move = button - FIRST_MOVE;
        return switch (mode) {
            case ROULETTE -> switch (move) {
                case 0 -> "red";
                case 1 -> "black";
                default -> "green";
            };
            case CRASH -> switch (move) {
                case 0 -> "bet";
                case 1 -> phase == 3 ? "crash_wait" : "cash_out";
                default -> "info";
            };
            case BLACKJACK -> switch (move) {
                // Nothing is running: the button that begins a hand says so.
                case 0 -> phase <= 0 ? "start" : "deal";
                case 1 -> "hit";
                default -> "stand";
            };
            case CASE_BATTLE -> switch (move) {
                case 0 -> "battle_join";
                case 1 -> "battle_start";
                default -> "battle_leave";
            };
            case BINGO -> switch (move) {
                case 0 -> "bingo_buy";
                case 1 -> "bingo_board";
                default -> "info";
            };
            default -> switch (move) {
                case 0 -> "previous";
                case 1 -> "play";
                default -> "next";
            };
        };
    }

    public static String label(GameMode mode, int button) {
        return switch (button) {
            case DEPOSIT -> "deposit";
            case RETURN -> "return";
            case COLLECT -> "collect";
            case OPEN -> "details";
            default -> move(mode, button, 0);
        };
    }
}
