package dev.gamblingitems.core.cases;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CaseRulesTest {
    @Test
    void weightsAlwaysCoverTheWholeTable() {
        int[] weights = CaseRules.normalize(List.of(440L, 220L, 150L, 100L, 80L, 10L));
        assertEquals(CaseRules.TOTAL_WEIGHT, Arrays.stream(weights).sum());
        assertEquals(440_000, weights[0]);
        assertEquals(10_000, weights[5]);
    }

    @Test
    void roundingKeepsTheSumExact() {
        int[] weights = CaseRules.normalize(List.of(1L, 1L, 1L));
        assertEquals(CaseRules.TOTAL_WEIGHT, Arrays.stream(weights).sum());
        assertEquals(333_334, weights[0], "The largest remainder is assigned first");
    }

    @Test
    void announcedRewardsStayReachable() {
        List<Long> raw = new ArrayList<>(List.of(1_000_000L));
        for (int index = 0; index < 8; index++) raw.add(1L);
        int[] weights = CaseRules.normalize(raw);
        assertEquals(CaseRules.TOTAL_WEIGHT, Arrays.stream(weights).sum());
        for (int weight : weights) assertTrue(weight > 0, "A displayed reward must be drawable");
    }

    @Test
    void refusesTablesThatCannotBeDrawn() {
        assertThrows(IllegalArgumentException.class, () -> CaseRules.normalize(List.of(1L)));
        assertThrows(IllegalArgumentException.class, () -> CaseRules.normalize(List.of(1L, 0L)));
        assertThrows(IllegalArgumentException.class, () -> CaseRules.normalize(List.of(1L, -3L)));
        List<Long> tooMany = new ArrayList<>();
        for (int index = 0; index <= CaseRules.MAX_REWARDS; index++) tooMany.add(1L);
        assertThrows(IllegalArgumentException.class, () -> CaseRules.normalize(tooMany));
    }

    @Test
    void drawCoversEveryRewardAndNothingElse() {
        int[] weights = CaseRules.normalize(List.of(700L, 200L, 100L));
        assertEquals(0, CaseRules.select(weights, 0));
        assertEquals(0, CaseRules.select(weights, 699_999));
        assertEquals(1, CaseRules.select(weights, 700_000));
        assertEquals(2, CaseRules.select(weights, CaseRules.TOTAL_WEIGHT - 1));
        assertThrows(IllegalArgumentException.class, () -> CaseRules.select(weights, -1));
        assertThrows(IllegalArgumentException.class, () -> CaseRules.select(weights, CaseRules.TOTAL_WEIGHT));
    }

    @Test
    void probabilitiesComeFromTheWeightsThemselves() {
        int[] weights = CaseRules.normalize(List.of(3L, 1L));
        assertEquals(0, CaseRules.percentOf(weights[0]).setScale(2, RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("75.00")));
        assertEquals(0, CaseRules.percentOf(weights[1]).setScale(2, RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("25.00")));
    }

    @Test
    void averageAndReturnFollowTheAnnouncedTable() {
        int[] weights = CaseRules.normalize(List.of(440L, 220L, 150L, 100L, 80L, 10L));
        List<Long> values = List.of(600L, 800L, 800L, 1_500L, 2_000L, 5_000L);
        assertEquals(0, CaseRules.averageValue(weights, values).setScale(0, RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("920")), "Average of one opening");
        assertEquals(0, CaseRules.returnRate(weights, values, 1_000L).setScale(2, RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("0.92")));
        assertThrows(IllegalArgumentException.class, () -> CaseRules.returnRate(weights, values, 0));
        assertThrows(IllegalArgumentException.class,
                () -> CaseRules.averageValue(weights, List.of(600L, 800L)));
    }

    @Test
    void handlesLargeValuesWithoutOverflow() {
        int[] weights = CaseRules.normalize(List.of(999L, 1L));
        List<Long> values = List.of(1_000L, 1_000_000_000L);
        assertEquals(0, CaseRules.averageValue(weights, values).setScale(0, RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("1000999")));
    }
}
