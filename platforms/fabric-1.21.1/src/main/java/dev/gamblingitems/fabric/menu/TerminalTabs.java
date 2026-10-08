package dev.gamblingitems.fabric.menu;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.item.TerminalItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** The tabs of a portable item. Their buttons sit above every id a game window uses for itself. */
public final class TerminalTabs {
    public static final int FIRST = 40_000;
    private TerminalTabs() {}

    public static int button(GameMode mode) { return FIRST + mode.ordinal(); }
    public static boolean matches(int button) { return button >= FIRST && button < FIRST + GameMode.values().length; }

    public static boolean handle(Player player, int button) {
        if (!(player instanceof ServerPlayer server) || player.isSpectator() || !matches(button)) return false;
        GameMode mode = GameMode.values()[button - FIRST];
        return TerminalItem.hasAccess(player, mode) && GameMenus.open(server, mode, ContainerLevelAccess.NULL);
    }
}
