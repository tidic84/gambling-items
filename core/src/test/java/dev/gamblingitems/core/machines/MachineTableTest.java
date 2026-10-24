package dev.gamblingitems.core.machines;

import dev.gamblingitems.core.GameMode;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MachineTableTest {
    private static final MachineTable WHEEL = new MachineTable(GameMode.LUCKY_WHEEL,
            new int[] {0, 150, 0, 200, 50, 500, 0, 150, 50, 300, 0, 1500},
            new int[] {10, 8, 10, 6, 8, 2, 10, 8, 8, 4, 10, 1});
    private static final MachineTable PACHINKO = new MachineTable(GameMode.PACHINKO,
            new int[] {560, 210, 110, 100, 40, 100, 110, 210, 560}, new int[0]);
    private static final MachineTable CLAW = new MachineTable(GameMode.PRIZE_CLAW,
            new int[] {150, 2500}, new int[] {6_400, 380});
    private static final MachineTable HIGH_STAKES = new MachineTable(GameMode.HIGH_STAKES,
            new int[] {125, 200, 10_000}, new int[0]);

    /** Answers the given draws in order, checking each against its bound. */
    private static IntUnaryOperator draws(int... values) {
        Deque<Integer> queue = new ArrayDeque<>();
        for (int value : values) queue.add(value);
        return bound -> queue.remove();
    }

    @Test
    void aWheelStopsOnTheSectorItsTicketFallsIn() {
        // Tickets 0-9 are the first sector, 10-17 the second, and so on; the last ticket is the jackpot.
        assertEquals(0, WHEEL.play(0, draws(9, 0)).multiplier());
        assertArrayEquals(new int[] {1, 500}, WHEEL.play(0, draws(10, 500)).shown());
        assertEquals(150, WHEEL.play(0, draws(17, 0)).multiplier());
        assertEquals(1500, WHEEL.play(0, draws(84, 999)).multiplier());
        assertThrows(IllegalArgumentException.class, () -> WHEEL.play(0, draws(85, 0)), "A ticket past the wheel is refused");
    }

    @Test
    void theDefaultTablesGiveBackWhatTheyAnnounce() {
        assertEquals(new BigDecimal("0.952941"), WHEEL.returnToPlayer(0));
        assertEquals(new BigDecimal("0.962500"), PACHINKO.returnToPlayer(0));
        assertEquals(new BigDecimal("0.960000"), CLAW.returnToPlayer(0));
        assertEquals(new BigDecimal("0.950000"), CLAW.returnToPlayer(1));
        for (int choice = 0; choice < HIGH_STAKES.choices(); choice++) {
            assertEquals(0, BigDecimal.ONE.compareTo(HIGH_STAKES.returnToPlayer(choice)), "Every target is fair before the return rate");
        }
    }

    @Test
    void aSectorIsDrawnInProportionToItsWeight() {
        Random random = new Random(42);
        int[] seen = new int[12];
        int plays = 850_000;
        for (int play = 0; play < plays; play++) seen[WHEEL.play(0, random::nextInt).shown()[0]]++;
        int[] weights = WHEEL.weights();
        for (int sector = 0; sector < seen.length; sector++) {
            double expected = plays * weights[sector] / 85.0;
            assertEquals(expected, seen[sector], expected * 0.03 + 30, "Sector " + sector);
        }
    }

    @Test
    void aPachinkoBallLandsWhereItsPathLeadsIt() {
        // Right, right, left five times, right: three rights, the fourth pocket.
        MachineTable.Outcome outcome = PACHINKO.play(0, draws(1, 1, 0, 0, 0, 0, 0, 1));
        assertEquals(3, outcome.shown()[0]);
        assertEquals(0b1000_0011, outcome.shown()[1]);
        assertEquals(100, outcome.multiplier());
        assertArrayEquals(new int[] {1, 8, 28, 56, 70, 56, 28, 8, 1}, PACHINKO.weights(), "The weights are the binomial ones");
        assertEquals(new BigDecimal("0.27343750"), PACHINKO.chance(0, 4), "70 of 256 balls reach the middle");
    }

    @Test
    void aClawHoldsItsPrizeOnlyWithinItsGrip() {
        assertEquals(2500, CLAW.play(1, draws(379)).multiplier());
        assertArrayEquals(new int[] {1, 1}, CLAW.play(1, draws(0)).shown());
        assertEquals(0, CLAW.play(1, draws(380)).multiplier(), "The ticket just past the grip slips");
        assertArrayEquals(new int[] {1, 0}, CLAW.play(1, draws(9_999)).shown());
        assertEquals(new BigDecimal("0.03800000"), CLAW.chance(1, 1));
        assertThrows(IllegalArgumentException.class, () -> CLAW.play(2, draws(0)), "No third position");
    }

    @Test
    void aTargetIsReachedWithTheChanceOfOneHundredInTarget() {
        // 2x: the needle stops on one of 200 hundredths, the first 100 of which win.
        assertEquals(200, HIGH_STAKES.play(1, draws(99)).multiplier());
        assertEquals(0, HIGH_STAKES.play(1, draws(100)).multiplier());
        assertEquals(new BigDecimal("0.80000000"), HIGH_STAKES.chance(0, 1), "1.25x is reached four times in five");
        assertEquals(new BigDecimal("0.01000000"), HIGH_STAKES.chance(2, 1), "100x once in a hundred");
        assertEquals(10_000, HIGH_STAKES.bestMultiplier());
    }

    @Test
    void aPayoutIsTheStakeTimesThePrizeRoundedDown() {
        BigDecimal full = BigDecimal.ONE, house = new BigDecimal("0.96");
        assertEquals(0, MachineTable.payout(1_000, 0, full));
        assertEquals(500, MachineTable.payout(1_000, 50, full), "Half the stake back");
        assertEquals(1_920, MachineTable.payout(1_000, 200, house));
        assertEquals(1, MachineTable.payout(3, 50, full), "1.5 is paid as 1");
        assertThrows(IllegalArgumentException.class, () -> MachineTable.payout(0, 100, full));
        assertThrows(IllegalArgumentException.class, () -> MachineTable.payout(10, 100, new BigDecimal("1.01")));
    }

    @Test
    void anInvalidTableIsRefusedWithItsReason() {
        assertThrows(IllegalArgumentException.class, () -> new MachineTable(GameMode.LUCKY_WHEEL, new int[] {100}, new int[] {0}),
                "Weights adding up to nothing");
        assertThrows(IllegalArgumentException.class, () -> new MachineTable(GameMode.COIN_PUSHER, new int[] {100, 200}, new int[] {1}),
                "A prize without a weight");
        assertThrows(IllegalArgumentException.class, () -> new MachineTable(GameMode.CAPSULE_GACHA, new int[] {-1}, new int[] {1}));
        assertThrows(IllegalArgumentException.class, () -> new MachineTable(GameMode.PRIZE_CLAW, new int[] {100}, new int[] {10_001}));
        assertThrows(IllegalArgumentException.class, () -> new MachineTable(GameMode.PACHINKO, new int[] {100, 100}, new int[0]));
        assertThrows(IllegalArgumentException.class, () -> new MachineTable(GameMode.HIGH_STAKES, new int[] {100}, new int[0]),
                "1x is not a target");
        assertThrows(IllegalArgumentException.class, () -> new MachineTable(GameMode.SLOT_MACHINE, new int[] {100}, new int[] {1}),
                "Slots keep their own printed paytable");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new MachineTable(GameMode.LUCKY_WHEEL, new int[] {100}, new int[] {-3}));
        assertTrue(error.getMessage().contains("-3"), "The message names the wrong value");
    }

    @Test
    void anUnreachablePrizeIsNotTheBest() {
        MachineTable gacha = new MachineTable(GameMode.CAPSULE_GACHA, new int[] {50, 90_000}, new int[] {1, 0});
        assertEquals(50, gacha.bestMultiplier());
        int[] multipliers = {50, 100};
        MachineTable table = new MachineTable(GameMode.CAPSULE_GACHA, multipliers, new int[] {1, 1});
        multipliers[0] = 9;
        assertEquals(50, table.multipliers()[0], "A table keeps its own copy");
    }
}
