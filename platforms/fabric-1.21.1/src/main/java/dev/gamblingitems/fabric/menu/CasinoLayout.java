package dev.gamblingitems.fabric.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The grid every terminal game shares, read by the menus for their slots and by the screens for
 * what they draw, so both sides cannot drift apart.
 *
 * <pre>
 *  header (title or tabs)                         0..28
 *  game card (left)        | side card (right)   36..144
 *  inventory 9x4           | betting slip       152..232
 * </pre>
 */
public final class CasinoLayout {
    public static final int WIDTH = 376, HEIGHT = 240;
    public static final int CONTENT_Y = 36, CONTENT_HEIGHT = 108;
    public static final int GAME_X = 10, GAME_WIDTH = 236;
    public static final int SIDE_X = 252, SIDE_WIDTH = 114;
    public static final int INVENTORY_X = 12, INVENTORY_Y = 156, HOTBAR_Y = 214;
    public static final int SLIP_X = 184, SLIP_Y = 152, SLIP_WIDTH = 182, SLIP_HEIGHT = 80;

    private CasinoLayout() {}

    /** The player's inventory, as vanilla orders it: three rows, then the hotbar a little apart. */
    public static void inventory(Inventory inventory, java.util.function.Consumer<Slot> add) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                add.accept(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
        for (int col = 0; col < 9; col++) add.accept(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
    }
}
