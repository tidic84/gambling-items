package dev.gamblingitems.fabric.block;

import dev.gamblingitems.core.GameMode;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;

/** Shared geometry for the world renderer and server-side hit testing. Coordinates are pixels. */
public final class StationPanel {
    public static final int LEFT = -180, TOP = -10, CELL_WIDTH = 120, ROW_HEIGHT = 20;
    private StationPanel() {}

    public record Control(int action, int x, int y, int width, int height, String label) {}

    public static java.util.List<Control> controls(GameMode mode, int phase) {
        if (mode == GameMode.CRASH) return java.util.List.of(
                new Control(4, LEFT, TOP, 358, 28, phase == 2 ? "cash_out" : phase == 3 ? "crash_wait" : "bet"),
                new Control(0, LEFT, TOP + 32, 178, 24, "crash_deposit"),
                new Control(6, 0, TOP + 32, 178, 24, "crash_recover"));
        var controls = new java.util.ArrayList<Control>();
        for (int button = 0; button < 9; button++) controls.add(new Control(button,
                LEFT + button % 3 * CELL_WIDTH, TOP + button / 3 * ROW_HEIGHT,
                CELL_WIDTH - 2, ROW_HEIGHT - 2, label(mode, button)));
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

    public static String label(GameMode mode, int button) {
        return switch (button) {
            case 0 -> "deposit_one";
            case 1 -> "deposit_stack";
            case 2 -> "return";
            case 3 -> mode == GameMode.ROULETTE ? "red" : mode == GameMode.CRASH ? "bet" : "previous";
            case 4 -> mode == GameMode.ROULETTE ? "black" : mode == GameMode.CRASH ? "cash_out" : "play";
            case 5 -> mode == GameMode.ROULETTE ? "green" : mode == GameMode.CRASH ? "info" : "next";
            case 6 -> "collect";
            case 7 -> "info";
            case 8 -> "details";
            default -> "info";
        };
    }
}
