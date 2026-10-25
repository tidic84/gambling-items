package dev.gamblingitems.core.machines;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.CabinetControls.Control;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CabinetControlsTest {
    @Test
    void theLeverAndTheSpinButtonPullTheSlotMachine() {
        // The lever stands past the right flank: the click lands on that flank.
        assertEquals(Control.PLAY, CabinetControls.at(GameMode.SLOT_MACHINE, 16, 22, 6));
        assertEquals(Control.PLAY, CabinetControls.at(GameMode.SLOT_MACHINE, 12, 14.6, 0));
        // The reels and the paytable only open the window.
        assertEquals(Control.NONE, CabinetControls.at(GameMode.SLOT_MACHINE, 8, 24, 0));
        assertEquals(Control.NONE, CabinetControls.at(GameMode.SLOT_MACHINE, 8, 7, 0));
    }

    @Test
    void theClawAndTheTerminalHaveAControlToMoveAndOneToPlay() {
        assertEquals(Control.PLAY, CabinetControls.at(GameMode.PRIZE_CLAW, 12, 14, 0));
        assertEquals(Control.NEXT, CabinetControls.at(GameMode.PRIZE_CLAW, 4, 15, 0));
        assertEquals(Control.PLAY, CabinetControls.at(GameMode.HIGH_STAKES, 3.5, 14, 0));
        assertEquals(Control.NEXT, CabinetControls.at(GameMode.HIGH_STAKES, 12, 14, 0));
        assertEquals(Control.NONE, CabinetControls.at(GameMode.HIGH_STAKES, 8, 24, 6));
    }

    @Test
    void everyMachineCanBePlayedFromItsFront() {
        double[][] fronts = {{8, 25.5}, {8, 13.5}, {15, 10}, {8, 9.5}};
        GameMode[] modes = {GameMode.LUCKY_WHEEL, GameMode.COIN_PUSHER, GameMode.PACHINKO, GameMode.CAPSULE_GACHA};
        for (int index = 0; index < modes.length; index++)
            assertEquals(Control.PLAY, CabinetControls.at(modes[index], fronts[index][0], fronts[index][1], 0), modes[index].id());
    }

    @Test
    void theArcadeMachinesHaveAButtonPerChoice() {
        assertEquals(2, CabinetControls.width(GameMode.PLINKO));
        assertEquals(1, CabinetControls.width(GameMode.VIDEO_POKER));
        assertEquals(Control.PLAY, CabinetControls.at(GameMode.PLINKO, 16, 15, 0));
        assertEquals(Control.PICK_7, CabinetControls.at(GameMode.BIG_SIX, CabinetControls.SYMBOL_U + 6 * CabinetControls.SYMBOL_STEP + 1, 11, 0));
        assertEquals(6, Control.PICK_7.pick());
        assertEquals(-1, Control.PLAY.pick());
        assertEquals(Control.PICK_6, CabinetControls.at(GameMode.HORSE_RACE, CabinetControls.LANE_U + 5 * CabinetControls.LANE_STEP + 1, 13, 0));
        // The third card answers on the screen and at its hold button.
        assertEquals(Control.PICK_3, CabinetControls.at(GameMode.VIDEO_POKER, CabinetControls.CARD_U + 2 * CabinetControls.CARD_STEP + 1, 23, 2));
        assertEquals(Control.PICK_3, CabinetControls.at(GameMode.VIDEO_POKER, CabinetControls.HOLD_U + 2 * CabinetControls.HOLD_STEP + 1, 14, 0));
        assertEquals(Control.PLAY, CabinetControls.at(GameMode.VIDEO_POKER, 13.8, 14, 0));
    }

    @Test
    void theTableGamesHaveTheirBetsAlongTheDeck() {
        assertEquals(Control.PICK_1, CabinetControls.at(GameMode.CRAPS, 3, 14, 0));
        assertEquals(Control.PICK_2, CabinetControls.at(GameMode.CRAPS, 13, 14, 0));
        assertEquals(Control.NONE, CabinetControls.at(GameMode.CRAPS, 8, 14, 0), "The dice tray is no bet");
        // Player, tie, banker from left to right; the tie is the third choice.
        assertEquals(Control.PICK_1, CabinetControls.at(GameMode.BACCARAT, 3, 14, 0));
        assertEquals(Control.PICK_3, CabinetControls.at(GameMode.BACCARAT, 8, 14, 0));
        assertEquals(Control.PICK_2, CabinetControls.at(GameMode.BACCARAT, 13, 14, 0));
        assertEquals(Control.PLAY, CabinetControls.at(GameMode.KENO, 12.5, 14, 0));
        assertEquals(Control.PLAY, CabinetControls.at(GameMode.SCRATCH_CARD, 12.5, 14, 0));
        assertEquals(1, CabinetControls.width(GameMode.BACCARAT));
    }

    @Test
    void stationsHaveNoCabinetControls() {
        assertEquals(Control.NONE, CabinetControls.at(GameMode.ROULETTE, 8, 8, 0));
    }
}
