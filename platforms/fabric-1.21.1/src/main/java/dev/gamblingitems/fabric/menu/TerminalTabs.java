package dev.gamblingitems.fabric.menu;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.item.TerminalItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** The tabs of a portable item. Their buttons sit above every id a game window uses for itself. */
public final class TerminalTabs {
    public static final int FIRST = 40_000;
    /** The prediction market is a tab of the pocket casino without being one of its games. */
    public static final int PREDICTION = FIRST + 500;
    private TerminalTabs() {}

    public static int button(GameMode mode) { return FIRST + mode.ordinal(); }
    public static boolean matches(int button) {
        return button == PREDICTION || button >= FIRST && button < FIRST + GameMode.values().length;
    }

    public static boolean handle(Player player, int button) {
        if (!(player instanceof ServerPlayer server) || player.isSpectator() || !matches(button)) return false;
        if (button == PREDICTION) {
            if (!TerminalItem.hasAccess(player, GameMode.CRASH)) return false;
            dev.gamblingitems.fabric.prediction.PredictionMenus.open(server, ContainerLevelAccess.NULL);
            return true;
        }
        GameMode mode = GameMode.values()[button - FIRST];
        return TerminalItem.hasAccess(player, mode) && GameMenus.open(server, mode, ContainerLevelAccess.NULL);
    }
}
