package dev.gamblingitems.core.upgrade;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class UpgradeRulesTest {
    private final UpgradeRules rules = new UpgradeRules(new BigDecimal("0.90"), new BigDecimal("0.90"));

    @Test
    void documentedExampleGivesThirtySixPercent() {
        assertEquals(0, new BigDecimal("0.36").compareTo(rules.chance(10, 25)));
    }

    @ParameterizedTest
    @CsvSource({"0,25", "-1,25", "25,25", "26,25", "1,-1"})
    void rejectsEmptyStakesAndNonUpgrades(long input, long reward) {
        assertThrows(IllegalArgumentException.class, () -> rules.chance(input, reward));
    }

    @Test
    void doesNotRaiseVerySmallChancesToAnArtificialMinimum() {
        assertTrue(rules.chance(1, 1_000_000).compareTo(new BigDecimal("0.001")) < 0);
    }

    @Test
    void doesNotOverflowWhenUsingLargeValues() {
        BigDecimal chance = rules.chance(Long.MAX_VALUE - 1, Long.MAX_VALUE);
        assertTrue(chance.signum() > 0);
        assertTrue(chance.compareTo(new BigDecimal("0.90")) <= 0);
    }

    @Test
    void respectsConfiguredMaximum() {
        UpgradeRules capped = new UpgradeRules(BigDecimal.ONE, new BigDecimal("0.50"));
        assertEquals(0, new BigDecimal("0.50").compareTo(capped.chance(99, 100)));
    }

    @ParameterizedTest
    @CsvSource({"0,0.9", "-0.1,0.9", "1.1,0.9", "0.9,0", "0.9,1.1"})
    void rejectsInvalidConfiguration(BigDecimal rate, BigDecimal cap) {
        assertThrows(IllegalArgumentException.class, () -> new UpgradeRules(rate, cap));
    }

    @Test
    void aMoreValuableTargetCannotIncreaseTheChance() {
        assertTrue(rules.chance(10, 50).compareTo(rules.chance(10, 25)) < 0);
    }

    @Test
    void changingTheValueUnitDoesNotChangeTheChance() {
        assertEquals(0, rules.chance(10, 25).compareTo(rules.chance(10_000, 25_000)));
    }
}

