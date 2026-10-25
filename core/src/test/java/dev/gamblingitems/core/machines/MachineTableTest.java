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

    private static final MachineTable PLINKO = new MachineTable(GameMode.PLINKO,
            new int[] {4100, 1100, 400, 180, 110, 50, 40, 50, 110, 180, 400, 1100, 4100}, new int[0]);
    private static final MachineTable BIG_SIX = new MachineTable(GameMode.BIG_SIX,
            new int[] {200, 300, 600, 1100, 2100, 4100, 4100}, new int[] {24, 15, 7, 4, 2, 1, 1});
    private static final MachineTable RACE = new MachineTable(GameMode.HORSE_RACE,
            new int[] {270, 380, 520, 790, 1350, 3100}, new int[] {35, 25, 18, 12, 7, 3});

    @Test
    void theArcadeTablesGiveBackWhatTheyAnnounce() {
        assertEquals(new BigDecimal("0.956201"), PLINKO.returnToPlayer(0));
        assertEquals(4096, java.util.Arrays.stream(PLINKO.weights()).sum(), "Twelve rows: 4096 paths");
        assertEquals(new BigDecimal("0.888889"), BIG_SIX.returnToPlayer(0), "The real big six: 1 to 1 on 24 of 54");
        assertEquals(new BigDecimal("0.759259"), BIG_SIX.returnToPlayer(6));
        for (int horse = 0; horse < RACE.choices(); horse++) {
            assertTrue(RACE.returnToPlayer(horse).compareTo(new BigDecimal("0.95")) <= 0, "Horse " + horse);
        }
        assertEquals(VideoPokerRules.STANDARD_RETURN,
                new MachineTable(GameMode.VIDEO_POKER, VideoPokerRules.PAYTABLE, new int[0]).returnToPlayer(0));
    }

    @Test
    void onlyTheSymbolOrTheHorseBetOnPays() {
        assertEquals(7, BIG_SIX.choices());
        // Tickets 0-23 are the 24 segments of the first symbol; the second segment of it is shown.
        MachineTable.Outcome first = BIG_SIX.play(0, draws(5, 1));
        assertEquals(200, first.multiplier());
        assertArrayEquals(new int[] {0, 1}, first.shown());
        assertEquals(0, BIG_SIX.play(1, draws(5, 1)).multiplier(), "Another symbol bet loses");
        assertEquals(4100, BIG_SIX.play(6, draws(53, 0)).multiplier(), "The last segment is the second joker");
        assertEquals(3100, RACE.play(5, draws(99, 400)).multiplier());
        assertEquals(0, RACE.play(4, draws(99, 400)).multiplier());
        assertEquals(new BigDecimal("0.03000000"), RACE.chance(5, 1));
    }

    @Test
    void aPlinkoBallFallsThroughTwelveRows() {
        MachineTable.Outcome outcome = PLINKO.play(0, draws(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1));
        assertArrayEquals(new int[] {12, 0b1111_1111_1111}, outcome.shown());
        assertEquals(4100, outcome.multiplier());
        assertThrows(IllegalArgumentException.class, () -> new MachineTable(GameMode.PLINKO, new int[9], new int[0]),
                "A plinko board has thirteen pockets");
    }

    @Test
    void aVideoPokerPaytableNeverPaysAboveTheStandardOne() {
        int[] richer = VideoPokerRules.PAYTABLE.clone();
        richer[VideoPokerRules.FULL_HOUSE]++;
        assertThrows(IllegalArgumentException.class, () -> new MachineTable(GameMode.VIDEO_POKER, richer, new int[0]));
        int[] poorer = VideoPokerRules.PAYTABLE.clone();
        poorer[VideoPokerRules.FULL_HOUSE] = 800;
        assertEquals(80_000, new MachineTable(GameMode.VIDEO_POKER, poorer, new int[0]).bestMultiplier());
        assertThrows(IllegalStateException.class,
                () -> new MachineTable(GameMode.VIDEO_POKER, poorer, new int[0]).play(0, bound -> 0));
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
