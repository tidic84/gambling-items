package dev.gamblingitems.fabric.menu;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.battle.BattleMenus;
import dev.gamblingitems.fabric.bingo.BingoMenus;
import dev.gamblingitems.fabric.blackjack.BlackjackMenus;
import dev.gamblingitems.fabric.cases.CaseMenus;
import dev.gamblingitems.fabric.crash.CrashMenus;
import dev.gamblingitems.fabric.roulette.RouletteMenus;
import dev.gamblingitems.fabric.slots.SlotMenus;
import dev.gamblingitems.fabric.tradeup.TradeUpMenus;
import dev.gamblingitems.fabric.upgrade.UpgradeMenus;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** Single entry point to open a game, from the portable terminal or from a placed station. */
public final class GameMenus {
    /** Games that are actually implemented today; the others are only announced. */
    public static final Set<GameMode> AVAILABLE = EnumSet.of(GameMode.UPGRADER, GameMode.TRADE_UP, GameMode.CASE_OPENING, GameMode.CRASH,
            GameMode.ROULETTE, GameMode.BLACKJACK, GameMode.CASE_BATTLE,
            GameMode.BINGO, GameMode.SLOT_MACHINE);

    private GameMenus() {}

    public static boolean open(ServerPlayer player, GameMode mode, ContainerLevelAccess access) {
        if (player.isSpectator() || !AVAILABLE.contains(mode)) return false;
        dev.gamblingitems.fabric.block.StationInteractions.close(player.getUUID());
        switch (mode) {
            case UPGRADER -> UpgradeMenus.open(player, access);
            case TRADE_UP -> TradeUpMenus.open(player, access);
            case CASE_OPENING -> CaseMenus.open(player, access);
            case CRASH -> CrashMenus.open(player, access);
            case ROULETTE -> RouletteMenus.open(player, access);
            case BLACKJACK -> BlackjackMenus.open(player, access);
            case CASE_BATTLE -> BattleMenus.open(player, access);
            case BINGO -> BingoMenus.open(player, access);
            case SLOT_MACHINE -> SlotMenus.open(player, access);
            default -> {
                return false;
            }
        }
        return true;
    }
}
