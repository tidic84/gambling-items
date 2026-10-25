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
    void stationsHaveNoCabinetControls() {
        assertEquals(Control.NONE, CabinetControls.at(GameMode.ROULETTE, 8, 8, 0));
    }
}
