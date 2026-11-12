package dev.gamblingitems.core.cheat;

import dev.gamblingitems.core.cheat.CheatRules.Settings;
import dev.gamblingitems.core.cheat.CheatRules.Tool;
import dev.gamblingitems.core.roulette.RouletteWheel;
import dev.gamblingitems.core.roulette.RouletteWheel.Bet;
import dev.gamblingitems.core.roulette.RouletteWheel.BetType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheatRulesTest {
    private static final Settings SETTINGS = new Settings(60, 1_200, 9_000, 30_000, 600_000, 80);

    @Test
    void aColdPlayerAloneOnlyRisksTheTrickItself() {
        assertEquals(1_500, CheatRules.detectionChance(SETTINGS, Tool.MARKED_LENS.defaultBaseChance(), 0, 0));
    }

    @Test
    void suspicionAndCamerasAddUpButNeverMakeATrickHopeless() {
        assertEquals(1_500 + 20 * 60 + 2 * 1_200, CheatRules.detectionChance(SETTINGS, 1_500, 20, 2));
        assertEquals(9_000, CheatRules.detectionChance(SETTINGS, 2_500, 100, 10), "Capped by the settings");
        assertEquals(CheatRules.detectionChance(SETTINGS, 1_000, CheatRules.MAX_HEAT, 0),
                CheatRules.detectionChance(SETTINGS, 1_000, 500, 0), "Heat above the maximum counts as the maximum");
        assertThrows(IllegalArgumentException.class, () -> CheatRules.detectionChance(SETTINGS, 1_000, -1, 0));
    }

    @Test
    void aDrawBelowTheChanceIsSpotted() {
        assertTrue(CheatRules.spotted(1_500, 0));
        assertTrue(CheatRules.spotted(1_500, 1_499));
        assertFalse(CheatRules.spotted(1_500, 1_500));
        assertFalse(CheatRules.spotted(0, 0), "A zero chance is never spotted");
        assertThrows(IllegalArgumentException.class, () -> CheatRules.spotted(1_500, 10_000));
    }

    @Test
    void heatWarmsWithTricksAndCoolsWithTime() {
        assertEquals(25, CheatRules.warmed(10, 15));
        assertEquals(CheatRules.MAX_HEAT, CheatRules.warmed(95, 15));
        assertEquals(40, CheatRules.cooled(SETTINGS, 40, 29_999), "Less than a period changes nothing");
        assertEquals(38, CheatRules.cooled(SETTINGS, 40, 60_000));
        assertEquals(0, CheatRules.cooled(SETTINGS, 40, Long.MAX_VALUE));
    }

    @Test
    void aMagnetDragsTheBallOnePocketTowardsTheBestPayout() {
        // 32 sits right after zero on the wheel; 26 sits right before it.
        int zeroPocket = RouletteWheel.pocketOf(0);
        Bet red = new Bet(BetType.RED, 0);
        int number = CheatRules.magnet(zeroPocket, RouletteWheel.POCKETS, RouletteWheel::numberAtPocket,
                drawn -> RouletteWheel.wins(red, drawn) ? 2_000 : 0);
        assertEquals(32, number, "Zero becomes the red 32 for a player on red");
        Bet black = new Bet(BetType.BLACK, 0);
        assertEquals(26, CheatRules.magnet(zeroPocket, RouletteWheel.POCKETS, RouletteWheel::numberAtPocket,
                drawn -> RouletteWheel.wins(black, drawn) ? 2_000 : 0), "And the black 26 for one on black");
        Bet far = new Bet(BetType.STRAIGHT, 17);
        assertEquals(0, CheatRules.magnet(zeroPocket, RouletteWheel.POCKETS, RouletteWheel::numberAtPocket,
                drawn -> RouletteWheel.wins(far, drawn) ? 36_000 : 0), "A number far away cannot be reached");
    }

    @Test
    void aMagnetKeepsAWinningNumber() {
        int pocket = RouletteWheel.pocketOf(17);
        Bet seventeen = new Bet(BetType.STRAIGHT, 17);
        assertEquals(17, CheatRules.magnet(pocket, RouletteWheel.POCKETS, RouletteWheel::numberAtPocket,
                drawn -> RouletteWheel.wins(seventeen, drawn) ? 36_000 : 0));
    }

    @Test
    void toolsAreReadBackFromTheirIdentifier() {
        for (Tool tool : Tool.values()) assertEquals(tool, Tool.fromId(tool.id()));
        assertThrows(IllegalArgumentException.class, () -> Tool.fromId("lucky_charm"));
    }
}
