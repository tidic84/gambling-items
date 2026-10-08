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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** Single entry point to open a game, from the portable terminal or from a placed station. */
public final class GameMenus {
    /** Games that are actually implemented today; the others are only announced. */
    public static final Set<GameMode> AVAILABLE = EnumSet.of(GameMode.UPGRADER, GameMode.TRADE_UP, GameMode.CASE_OPENING, GameMode.CRASH,
            GameMode.ROULETTE, GameMode.BLACKJACK, GameMode.CASE_BATTLE,
            GameMode.BINGO, GameMode.SLOT_MACHINE);
    /** The item games of the exchange terminal, in the order of its tabs. */
    public static final List<GameMode> TERMINAL = List.of(GameMode.TRADE_UP, GameMode.UPGRADER,
            GameMode.CASE_OPENING, GameMode.CASE_BATTLE);
    /** The casino games of the pocket casino, in the order of its tabs. */
    public static final List<GameMode> CASINO = List.of(GameMode.CRASH, GameMode.ROULETTE,
            GameMode.BLACKJACK, GameMode.BINGO, GameMode.SLOT_MACHINE);
    /** Each portable item reopens on the game last played with it. */
    private static final Map<UUID, GameMode> LAST_TERMINAL_GAME = new HashMap<>(), LAST_CASINO_GAME = new HashMap<>();

    private GameMenus() {}

    /** The games sharing a portable item with this one: its tabs. */
    public static List<GameMode> family(GameMode mode) { return TERMINAL.contains(mode) ? TERMINAL : CASINO; }

    public static void openTerminal(ServerPlayer player) {
        open(player, LAST_TERMINAL_GAME.getOrDefault(player.getUUID(), TERMINAL.get(0)), ContainerLevelAccess.NULL);
    }

    public static void openCasino(ServerPlayer player) {
        open(player, LAST_CASINO_GAME.getOrDefault(player.getUUID(), CASINO.get(0)), ContainerLevelAccess.NULL);
    }

    /**
     * Without a block, which is what {@link ContainerLevelAccess#NULL} means here, the game is played
     * from a portable item; its menu checks that the player still carries it.
     */
    public static boolean open(ServerPlayer player, GameMode mode, ContainerLevelAccess access) {
        boolean portable = access == ContainerLevelAccess.NULL;
        if (player.isSpectator() || !AVAILABLE.contains(mode)) return false;
        dev.gamblingitems.fabric.block.StationInteractions.close(player.getUUID());
        if (portable) (TERMINAL.contains(mode) ? LAST_TERMINAL_GAME : LAST_CASINO_GAME).put(player.getUUID(), mode);
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
