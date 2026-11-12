package dev.gamblingitems.fabric.menu;

import dev.gamblingitems.fabric.battle.BattleMenu;
import dev.gamblingitems.fabric.bingo.BingoMenu;
import dev.gamblingitems.fabric.blackjack.BlackjackMenu;
import dev.gamblingitems.fabric.cases.CaseMenu;
import dev.gamblingitems.fabric.crash.CrashMenu;
import dev.gamblingitems.fabric.roulette.RouletteMenu;
import dev.gamblingitems.fabric.slots.SlotMenu;
import dev.gamblingitems.fabric.tradeup.TradeUpMenu;
import dev.gamblingitems.fabric.upgrade.UpgradeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Full-width menu actions for 1.20.1, whose vanilla button packet only carries a byte. */
public record MenuButton(int containerId, int button) {
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(button);
    }

    public static MenuButton read(FriendlyByteBuf buffer) {
        MenuButton action = new MenuButton(buffer.readVarInt(), buffer.readVarInt());
        if (buffer.isReadable()) throw new IllegalArgumentException("Trailing menu action data");
        return action;
    }

    /** Called on the server thread. Closed, replaced and foreign menus cannot receive actions. */
    public static boolean handle(ServerPlayer player, MenuButton action) {
        AbstractContainerMenu menu = player.containerMenu;
        if (player.isSpectator() || !player.isAlive() || action.button < 0
                || menu.containerId != action.containerId || !isGame(menu) || !menu.stillValid(player)) {
            return false;
        }
        boolean handled = menu.clickMenuButton(player, action.button);
        // A tab may have replaced the menu. Synchronize the one that is now open.
        if (handled) player.containerMenu.broadcastChanges();
        return handled;
    }

    private static boolean isGame(AbstractContainerMenu menu) {
        return menu instanceof UpgradeMenu || menu instanceof TradeUpMenu || menu instanceof CaseMenu
                || menu instanceof CrashMenu || menu instanceof RouletteMenu || menu instanceof BlackjackMenu
                || menu instanceof BattleMenu || menu instanceof BingoMenu || menu instanceof SlotMenu
                || menu instanceof dev.gamblingitems.fabric.prediction.PredictionMenu;
    }
}
