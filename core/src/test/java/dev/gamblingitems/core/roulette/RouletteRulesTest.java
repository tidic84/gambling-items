package dev.gamblingitems.core.roulette;

import dev.gamblingitems.core.roulette.RouletteRules.Colour;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouletteRulesTest {
    private final RouletteRules rules = new RouletteRules(7, 7, 1, 2, 14);

    @Test
    void theWheelHoldsExactlyTheConfiguredSlots() {
        assertEquals(15, rules.slots());
        Map<Colour, Integer> counted = new EnumMap<>(Colour.class);
        for (int slot = 0; slot < rules.slots(); slot++) {
            counted.merge(rules.colourAt(slot), 1, Integer::sum);
        }
        assertEquals(7, counted.get(Colour.RED));
        assertEquals(7, counted.get(Colour.BLACK));
        assertEquals(1, counted.get(Colour.GREEN));
        assertEquals(Colour.GREEN, rules.colourAt(0), "The green slots open the wheel");
        assertEquals(Colour.RED, rules.colourAt(1));
        assertEquals(Colour.BLACK, rules.colourAt(2), "Red and black alternate afterwards");
        assertThrows(IllegalArgumentException.class, () -> rules.colourAt(rules.slots()));
        assertThrows(IllegalArgumentException.class, () -> rules.colourAt(-1));
    }

    @Test
    void anUnevenWheelStillPlacesEverySlot() {
        RouletteRules uneven = new RouletteRules(5, 2, 1, 2, 8);
        Map<Colour, Integer> counted = new EnumMap<>(Colour.class);
        for (int slot = 0; slot < uneven.slots(); slot++) counted.merge(uneven.colourAt(slot), 1, Integer::sum);
        assertEquals(5, counted.get(Colour.RED));
        assertEquals(2, counted.get(Colour.BLACK));
        assertEquals(1, counted.get(Colour.GREEN));
    }

    @Test
    void everyBetReturnsTheSameShareOnThisWheel() {
        BigDecimal expected = new BigDecimal("0.933333");
        for (Colour colour : Colour.values()) {
            assertEquals(0, rules.returnRate(colour).compareTo(expected),
                    "Return of " + colour + " was " + rules.returnRate(colour));
        }
    }

    @Test
    void chancesComeFromTheWheelItself() {
        assertEquals(0, rules.chanceOf(Colour.RED).setScale(2, RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("46.67")));
        assertEquals(0, rules.chanceOf(Colour.GREEN).setScale(2, RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("6.67")));
    }

    @Test
    void theSpinCoversEverySlotAndNothingElse() {
        long bound = 1_500;
        Map<Colour, Integer> counted = new EnumMap<>(Colour.class);
        for (long draw = 0; draw < bound; draw++) {
            int slot = rules.spin(draw, bound);
            assertTrue(slot >= 0 && slot < rules.slots());
            counted.merge(rules.colourAt(slot), 1, Integer::sum);
        }
        assertEquals(700, counted.get(Colour.RED), "Each slot is drawn as often as any other");
        assertEquals(700, counted.get(Colour.BLACK));
        assertEquals(100, counted.get(Colour.GREEN));
        assertEquals(0, rules.spin(0, bound));
        assertEquals(rules.slots() - 1, rules.spin(bound - 1, bound));
        assertThrows(IllegalArgumentException.class, () -> rules.spin(bound, bound));
        assertThrows(IllegalArgumentException.class, () -> rules.spin(-1, bound));
    }

    @Test
    void payoutsIncludeTheStakeAndOnlyOnTheRightColour() {
        assertEquals(10, rules.payout(5, Colour.RED, Colour.RED), "Red pays twice the stake");
        assertEquals(0, rules.payout(5, Colour.RED, Colour.BLACK), "A losing bet pays nothing");
        assertEquals(0, rules.payout(5, Colour.RED, Colour.GREEN));
        assertEquals(70, rules.payout(5, Colour.GREEN, Colour.GREEN), "Green pays fourteen times");
        assertEquals(896, rules.maximumPayout(64), "A full stack on green is the largest win");
        assertThrows(IllegalArgumentException.class, () -> rules.payout(0, Colour.RED, Colour.RED));
    }

    @Test
    void rejectsImpossibleWheels() {
        assertThrows(IllegalArgumentException.class, () -> new RouletteRules(0, 7, 1, 2, 14));
        assertThrows(IllegalArgumentException.class, () -> new RouletteRules(7, 7, 0, 2, 14));
        assertThrows(IllegalArgumentException.class, () -> new RouletteRules(7, 7, 1, 1, 14), "A payout must beat the stake");
        assertThrows(IllegalArgumentException.class, () -> new RouletteRules(40, 40, 1, 2, 14), "Wheel too large");
    }
}
