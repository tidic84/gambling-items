package dev.gamblingitems.core.crash;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrashRulesTest {
    private final CrashRules rules = new CrashRules(new BigDecimal("0.95"), 5_000, new BigDecimal("1.02"));

    @Test
    void everyCashOutThresholdReturnsTheSameShare() {
        long bound = 1_000_000;
        for (int threshold : new int[] {150, 200, 500, 1_000, 2_000}) {
            long reached = 0;
            for (long draw = 0; draw < bound; draw++) {
                if (rules.crashPoint(draw, bound) >= threshold) reached++;
            }
            double ratio = (double) reached / bound * threshold / CrashRules.START;
            assertTrue(Math.abs(ratio - 0.95) < 0.001,
                    "Return at " + threshold + " hundredths was " + ratio);
        }
    }

    @Test
    void aDrawAboveTheReturnEndsTheFlightAtOnce() {
        long bound = 1_000;
        assertEquals(CrashRules.START, rules.crashPoint(bound - 1, bound), "The worst draw crashes at 1.00x");
        assertEquals(rules.maximumMultiplier(), rules.crashPoint(0, bound), "The best draw is capped");
        long instant = 0;
        for (long draw = 0; draw < bound; draw++) {
            if (rules.crashPoint(draw, bound) == CrashRules.START) instant++;
        }
        // Five percent of draws fall below 1.00x, plus the band that lands exactly on it.
        assertEquals(60, instant, "Those rounds pay nothing above the stake");
    }

    @Test
    void crashPointsNeverLeaveTheConfiguredRange() {
        long bound = 10_000;
        int previous = Integer.MAX_VALUE;
        for (long draw = 0; draw < bound; draw++) {
            int point = rules.crashPoint(draw, bound);
            assertTrue(point >= CrashRules.START && point <= rules.maximumMultiplier());
            assertTrue(point <= previous, "A larger draw never flies higher");
            previous = point;
        }
        assertThrows(IllegalArgumentException.class, () -> rules.crashPoint(bound, bound));
        assertThrows(IllegalArgumentException.class, () -> rules.crashPoint(-1, bound));
    }

    @Test
    void theMultiplierOnlyGrowsAndStopsAtTheCap() {
        assertEquals(CrashRules.START, rules.multiplierAt(0), "A flight starts at 1.00x");
        assertEquals(102, rules.multiplierAt(1));
        assertTrue(rules.multiplierAt(36) >= 200 && rules.multiplierAt(35) < 200, "Doubles in under two seconds");
        int previous = 0;
        for (int tick = 0; tick <= 400; tick++) {
            int multiplier = rules.multiplierAt(tick);
            assertTrue(multiplier >= previous, "The multiplier never falls");
            previous = multiplier;
        }
        assertEquals(rules.maximumMultiplier(), rules.multiplierAt(400), "The cap holds");
        assertThrows(IllegalArgumentException.class, () -> rules.multiplierAt(-1));
    }

    @Test
    void theFlightEndsOnTheFirstTickThatReachesThePoint() {
        int tick = rules.crashTick(200);
        assertTrue(rules.multiplierAt(tick) >= 200, "That tick reaches the point");
        assertTrue(rules.multiplierAt(tick - 1) < 200, "The previous tick did not");
        assertEquals(0, rules.crashTick(CrashRules.START), "An instant crash ends at the first tick");
        assertThrows(IllegalArgumentException.class, () -> rules.crashTick(CrashRules.START - 1));
        assertThrows(IllegalArgumentException.class, () -> rules.crashTick(rules.maximumMultiplier() + 1));
    }

    @Test
    void payoutsIncludeTheStakeAndRoundDown() {
        assertEquals(12, rules.payout(5, 240), "Five staked at 2.40x pays twelve in total");
        assertEquals(7, rules.payout(3, 250), "7.5 is paid as 7");
        assertEquals(1, rules.payout(1, CrashRules.START), "Cashing out at once returns the stake");
        assertEquals(500, rules.maximumPayout(10), "The cap bounds what a server can owe");
        assertThrows(IllegalArgumentException.class, () -> rules.payout(0, 200));
        assertThrows(IllegalArgumentException.class, () -> rules.payout(5, CrashRules.START - 1));
        assertThrows(IllegalArgumentException.class, () -> rules.payout(5, rules.maximumMultiplier() + 1));
    }

    @Test
    void rejectsImpossibleSettings() {
        assertThrows(IllegalArgumentException.class,
                () -> new CrashRules(BigDecimal.ZERO, 5_000, new BigDecimal("1.02")));
        assertThrows(IllegalArgumentException.class,
                () -> new CrashRules(new BigDecimal("1.5"), 5_000, new BigDecimal("1.02")));
        assertThrows(IllegalArgumentException.class,
                () -> new CrashRules(new BigDecimal("0.95"), CrashRules.START, new BigDecimal("1.02")));
        assertThrows(IllegalArgumentException.class,
                () -> new CrashRules(new BigDecimal("0.95"), 5_000, BigDecimal.ONE), "A flight must climb");
        assertThrows(IllegalArgumentException.class,
                () -> new CrashRules(new BigDecimal("0.95"), 5_000, new BigDecimal("2.5")));
    }
}
