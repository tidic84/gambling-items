package dev.gamblingitems.core.slots;

import java.math.BigDecimal;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlotRulesTest {
    @Test
    void aPullReadsThreeReelsAndCanBeReplayed() {
        int[] first = SlotRules.spin(new Random(7)::nextInt);
        int[] second = SlotRules.spin(new Random(7)::nextInt);
        assertArrayEquals(first, second, "The same draws show the same line");
        assertEquals(SlotRules.REELS, first.length);
        for (int symbol : first) {
            assertTrue(symbol >= 0 && symbol < SlotRules.SYMBOLS);
        }
        assertThrows(IllegalArgumentException.class, () -> SlotRules.spin(bound -> bound));
    }

    @Test
    void thePaytableReadsAsItIsPrinted() {
        for (int symbol = 0; symbol < SlotRules.SYMBOLS; symbol++) {
            assertEquals(SlotRules.tripleOf(symbol),
                    SlotRules.multiplier(new int[] {symbol, symbol, symbol}));
        }
        assertEquals(SlotRules.PAIR, SlotRules.multiplier(new int[] {0, 0, 1}));
        assertEquals(SlotRules.PAIR, SlotRules.multiplier(new int[] {1, 0, 0}));
        assertEquals(SlotRules.PAIR, SlotRules.multiplier(new int[] {0, 1, 0}));
        assertEquals(0, SlotRules.multiplier(new int[] {0, 1, 2}));
        assertThrows(IllegalArgumentException.class, () -> SlotRules.multiplier(new int[] {0, 1}));
        assertThrows(IllegalArgumentException.class, () -> SlotRules.multiplier(new int[] {0, 1, SlotRules.SYMBOLS}));
    }

    @Test
    void aDearerSymbolNeverPaysLess() {
        for (int symbol = 1; symbol < SlotRules.SYMBOLS; symbol++) {
            assertTrue(SlotRules.tripleOf(symbol) > SlotRules.tripleOf(symbol - 1),
                    "Symbol " + symbol + " must pay more than the one before it");
        }
        assertEquals(SlotRules.tripleOf(SlotRules.SYMBOLS - 1), SlotRules.bestMultiplier());
    }

    @Test
    void theCabinetKeepsAnHonestEdge() {
        BigDecimal rtp = SlotRules.returnToPlayer();
        assertTrue(rtp.compareTo(BigDecimal.ONE) < 0, "The house has to keep an edge: " + rtp);
        assertTrue(rtp.compareTo(new BigDecimal("0.85")) > 0, "But the cabinet stays playable: " + rtp);
    }

    @Test
    void aWinIsPaidStakeIncludedAndRoundedDown() {
        assertEquals(40, SlotRules.payout(10, 400, BigDecimal.ONE));
        assertEquals(14, SlotRules.payout(10, SlotRules.PAIR, BigDecimal.ONE));
        // Nine tenths of fourteen is twelve and six tenths: the player is given twelve.
        assertEquals(12, SlotRules.payout(10, SlotRules.PAIR, new BigDecimal("0.9")));
        assertEquals(0, SlotRules.payout(10, 0, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> SlotRules.payout(0, 400, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> SlotRules.payout(10, 400, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> SlotRules.payout(10, 400, new BigDecimal("1.5")));
    }
}
