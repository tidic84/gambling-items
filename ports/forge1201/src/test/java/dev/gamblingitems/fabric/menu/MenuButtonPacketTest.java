package dev.gamblingitems.fabric.menu;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.battle.BattleRules;
import dev.gamblingitems.core.roulette.RouletteWheel.Bet;
import dev.gamblingitems.core.roulette.RouletteWheel.BetType;
import dev.gamblingitems.fabric.battle.BattleMenu;
import dev.gamblingitems.fabric.bingo.BingoMenu;
import dev.gamblingitems.fabric.blackjack.BlackjackMenu;
import dev.gamblingitems.fabric.cases.CaseMenu;
import dev.gamblingitems.fabric.cases.CaseCatalog;
import dev.gamblingitems.fabric.crash.CrashMenu;
import dev.gamblingitems.fabric.roulette.RouletteMenu;
import dev.gamblingitems.fabric.slots.SlotMenu;
import dev.gamblingitems.fabric.tradeup.TradeUpMenu;
import dev.gamblingitems.fabric.upgrade.UpgradeMenu;
import dev.gamblingitems.fabric.value.ValueCatalog;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Uses the actual 1.20.1 buffer implementation, including every selectable target. */
class MenuButtonPacketTest {
    private static void roundTrip(int... buttons) {
        for (int button : buttons) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                MenuButton sent = new MenuButton(100, button);
                sent.write(buffer);
                assertEquals(sent, MenuButton.read(buffer), "Button " + button);
                assertFalse(buffer.isReadable());
            } finally {
                buffer.release();
            }
        }
    }

    @Test void slotsActions() { roundTrip(SlotMenu.SPIN_BUTTON, SlotMenu.COLLECT_BUTTON); }
    @Test void tradeUpAction() { roundTrip(TradeUpMenu.SPIN_BUTTON); }
    @Test void casesActionsAndSelections() {
        roundTrip(CaseMenu.OPEN_BUTTON);
        for (int selection = 0; selection < CaseCatalog.MAX_CASES; selection++) roundTrip(selection);
    }
    @Test void crashActions() {
        roundTrip(CrashMenu.BET_BUTTON, CrashMenu.CASH_OUT_BUTTON, CrashMenu.COLLECT_BUTTON);
    }
    @Test void blackjackActions() {
        roundTrip(BlackjackMenu.DEAL_BUTTON, BlackjackMenu.HIT_BUTTON, BlackjackMenu.STAND_BUTTON,
                BlackjackMenu.DOUBLE_BUTTON, BlackjackMenu.COLLECT_BUTTON);
    }
    @Test void bingoActions() { roundTrip(BingoMenu.BUY_BUTTON, BingoMenu.COLLECT_BUTTON); }
    @Test void battleActionsAndRoundCounts() {
        roundTrip(BattleMenu.JOIN_BUTTON, BattleMenu.LEAVE_BUTTON, BattleMenu.START_BUTTON,
                BattleMenu.COLLECT_BUTTON);
        for (int round = BattleRules.MIN_ROUNDS; round <= BattleRules.MAX_ROUNDS; round++)
            roundTrip(BattleMenu.ROUNDS_BUTTON + round);
        for (int selection = 0; selection < CaseCatalog.MAX_CASES; selection++) roundTrip(BattleMenu.CASE_BUTTON + selection);
    }
    @Test void everyRouletteAreaAndCollection() {
        roundTrip(RouletteMenu.COLLECT_BUTTON);
        for (BetType type : BetType.values()) {
            for (int choice = 0; choice < type.choices(); choice++) {
                Bet bet = new Bet(type, choice);
                int code = RouletteMenu.code(bet);
                assertEquals(bet, RouletteMenu.decode(code));
                roundTrip(RouletteMenu.BET_BUTTON + code);
            }
        }
    }
    @Test void everyUpgraderTargetAndSpin() {
        for (int target = 0; target < ValueCatalog.MAX_ENTRIES; target++) roundTrip(target);
        roundTrip(UpgradeMenu.SPIN_BUTTON);
    }
    @Test void everyPortableTab() {
        for (GameMode mode : GameMode.values()) {
            int button = TerminalTabs.button(mode);
            assertTrue(TerminalTabs.matches(button));
            roundTrip(button);
        }
    }
    @Test void malformedPayloadsAreRejected() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1);
            assertThrows(IndexOutOfBoundsException.class, () -> MenuButton.read(buffer));
            buffer.clear();
            new MenuButton(1, 1000).write(buffer);
            buffer.writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> MenuButton.read(buffer));
        } finally {
            buffer.release();
        }
    }
}
