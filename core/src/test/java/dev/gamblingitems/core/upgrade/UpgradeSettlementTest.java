package dev.gamblingitems.core.upgrade;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpgradeSettlementTest {
    private final UpgradeRules rules = new UpgradeRules(new BigDecimal("0.9"), new BigDecimal("0.9"));

    @Test void thresholdIsExclusive() {
        assertTrue(rules.wins(10, 25, new BigDecimal("0.359999")));
        assertFalse(rules.wins(10, 25, new BigDecimal("0.36")));
        assertFalse(rules.wins(10, 25, new BigDecimal("0.999999")));
    }

    @Test void invalidDrawCannotProduceAReward() {
        assertThrows(IllegalArgumentException.class, () -> rules.wins(10, 25, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> rules.wins(10, 25, new BigDecimal("-0.01")));
    }

    @Test void stacksAndDurabilityHaveConsistentValues() {
        assertEquals(8_000, ItemValues.stackValue(1_000, 8, 0, 0));
        assertEquals(500, ItemValues.stackValue(1_000, 1, 50, 100));
        assertEquals(0, ItemValues.stackValue(1_000, 1, 100, 100));
        assertEquals(333, ItemValues.stackValue(1_000, 1, 2, 3));
    }

    @Test void overflowAndInvalidDurabilityAreRejected() {
        assertThrows(ArithmeticException.class, () -> ItemValues.stackValue(Long.MAX_VALUE, 2, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ItemValues.stackValue(100, 1, 11, 10));
        assertThrows(IllegalArgumentException.class, () -> ItemValues.stackValue(100, 1, 1, 0));
    }
}
