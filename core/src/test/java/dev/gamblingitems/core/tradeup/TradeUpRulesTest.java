package dev.gamblingitems.core.tradeup;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradeUpRulesTest {
    private final TradeUpRules rules = new TradeUpRules(5, new BigDecimal("1.25"),
            new BigDecimal("0.71"), new BigDecimal("4"));

    @Test
    void acceptsFiveComparableUnitsOnly() {
        assertTrue(rules.acceptsStake(List.of(100L, 100L, 110L, 120L, 125L)));
        assertFalse(rules.acceptsStake(List.of(100L, 100L, 110L, 120L, 126L)), "Ratio above 1.25");
        assertFalse(rules.acceptsStake(List.of(100L, 100L, 100L, 100L)), "Four units");
        assertFalse(rules.acceptsStake(List.of(100L, 100L, 100L, 100L, 100L, 100L)), "Six units");
        assertFalse(rules.acceptsStake(List.of(100L, 100L, 100L, 100L, 0L)), "Unvalued item");
    }

    @Test
    void countsUnitsAndNotSlots() {
        List<Long> fromTwoStacks = new ArrayList<>(List.of(100L, 100L, 100L));
        assertFalse(rules.acceptsStake(fromTwoStacks));
        fromTwoStacks.add(100L);
        fromTwoStacks.add(100L);
        assertTrue(rules.acceptsStake(fromTwoStacks));
    }

    @Test
    void rewardsBeatEachInputWithoutBeatingTheirSum() {
        assertTrue(rules.isEligibleReward(20, 10, 50), "Worth more than one input");
        assertFalse(rules.isEligibleReward(10, 10, 50), "Same value as an input");
        assertTrue(rules.isEligibleReward(200, 10, 50), "Four times the stake stays allowed");
        assertFalse(rules.isEligibleReward(201, 10, 50), "Beyond the configured cap");
    }

    @Test
    void tableAveragesTheConfiguredReturn() {
        List<Long> rewards = List.of(20L, 45L, 100L);
        int[] weights = rules.weights(50, rewards);
        assertEquals(TradeUpRules.TOTAL_WEIGHT, java.util.Arrays.stream(weights).sum());
        BigDecimal average = rules.averageValue(weights, rewards);
        assertEquals(0, average.setScale(2, java.math.RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("35.50")), "Average of " + average);
    }

    @Test
    void cheaperRewardsAreAlwaysMoreLikely() {
        List<Long> rewards = List.of(12L, 20L, 35L, 60L, 90L, 150L);
        int[] weights = rules.weights(50, rewards);
        for (int index = 1; index < weights.length; index++) {
            assertTrue(weights[index] <= weights[index - 1],
                    "Reward " + rewards.get(index) + " must not beat a cheaper one");
            assertTrue(weights[index] > 0, "Announced rewards stay reachable");
        }
        assertEquals(0, rules.averageValue(weights, rewards)
                .setScale(0, java.math.RoundingMode.HALF_UP).compareTo(new BigDecimal("36")));
    }

    @Test
    void refusesTablesThatCannotReachTheReturn() {
        assertFalse(rules.canBuildTable(50, List.of(60L, 100L)), "Every reward above the target");
        assertFalse(rules.canBuildTable(50, List.of(11L, 20L)), "Every reward below the target");
        assertFalse(rules.canBuildTable(50, List.of(20L)), "A single reward is not a table");
        assertThrows(IllegalArgumentException.class, () -> rules.weights(50, List.of(60L, 100L)));
        assertThrows(IllegalArgumentException.class, () -> rules.weights(0, List.of(20L, 100L)));
    }

    @Test
    void refusesUnsortedRewards() {
        assertThrows(IllegalArgumentException.class, () -> rules.weights(50, List.of(20L, 100L, 45L)));
    }

    @Test
    void drawCoversEveryRewardAndNothingElse() {
        List<Long> rewards = List.of(20L, 45L, 100L);
        int[] weights = rules.weights(50, rewards);
        assertEquals(0, rules.select(weights, 0));
        assertEquals(weights.length - 1, rules.select(weights, TradeUpRules.TOTAL_WEIGHT - 1));
        assertEquals(1, rules.select(weights, weights[0]));
        assertThrows(IllegalArgumentException.class, () -> rules.select(weights, -1));
        assertThrows(IllegalArgumentException.class, () -> rules.select(weights, TradeUpRules.TOTAL_WEIGHT));
    }

    @Test
    void handlesWideCataloguesWithoutOverflow() {
        List<Long> rewards = List.of(1_000L, 10_000L, 100_000L, 1_000_000L, 100_000_000L);
        int[] weights = rules.weights(10_000_000L, rewards);
        assertEquals(TradeUpRules.TOTAL_WEIGHT, java.util.Arrays.stream(weights).sum());
        BigDecimal average = rules.averageValue(weights, rewards);
        assertTrue(average.compareTo(new BigDecimal("7099000")) > 0, "Average of " + average);
        assertTrue(average.compareTo(new BigDecimal("7101000")) < 0, "Average of " + average);
    }

    @Test
    void rejectsImpossibleSettings() {
        assertThrows(IllegalArgumentException.class,
                () -> new TradeUpRules(1, BigDecimal.ONE, new BigDecimal("0.71"), new BigDecimal("4")));
        assertThrows(IllegalArgumentException.class,
                () -> new TradeUpRules(5, new BigDecimal("0.9"), new BigDecimal("0.71"), new BigDecimal("4")));
        assertThrows(IllegalArgumentException.class,
                () -> new TradeUpRules(5, BigDecimal.ONE, new BigDecimal("1.5"), new BigDecimal("4")));
        assertThrows(IllegalArgumentException.class,
                () -> new TradeUpRules(5, BigDecimal.ONE, new BigDecimal("0.71"), BigDecimal.ONE));
    }
}
