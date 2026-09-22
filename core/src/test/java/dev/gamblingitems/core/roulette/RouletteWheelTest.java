package dev.gamblingitems.core.roulette;

import dev.gamblingitems.core.roulette.RouletteWheel.Bet;
import dev.gamblingitems.core.roulette.RouletteWheel.BetType;
import dev.gamblingitems.core.roulette.RouletteWheel.Colour;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouletteWheelTest {
    @Test
    void theWheelHoldsEveryNumberOnce() {
        Set<Integer> seen = new HashSet<>();
        for (int pocket = 0; pocket < RouletteWheel.POCKETS; pocket++) {
            assertTrue(seen.add(RouletteWheel.numberAtPocket(pocket)), "A number appears twice");
        }
        assertEquals(RouletteWheel.POCKETS, seen.size());
        assertEquals(0, RouletteWheel.numberAtPocket(0), "The wheel starts at zero");
        assertEquals(32, RouletteWheel.numberAtPocket(1), "The order is the European one");
        assertEquals(1, RouletteWheel.pocketOf(32));
        assertThrows(IllegalArgumentException.class, () -> RouletteWheel.numberAtPocket(RouletteWheel.POCKETS));
    }

    @Test
    void coloursFollowTheRealTable() {
        assertEquals(Colour.GREEN, RouletteWheel.colourOf(0));
        assertEquals(Colour.RED, RouletteWheel.colourOf(1));
        assertEquals(Colour.BLACK, RouletteWheel.colourOf(2));
        assertEquals(Colour.RED, RouletteWheel.colourOf(36));
        int reds = 0, blacks = 0;
        for (int number = 1; number < RouletteWheel.POCKETS; number++) {
            if (RouletteWheel.colourOf(number) == Colour.RED) reds++;
            else blacks++;
        }
        assertEquals(18, reds);
        assertEquals(18, blacks);
        assertThrows(IllegalArgumentException.class, () -> RouletteWheel.colourOf(37));
    }

    @Test
    void everyBetOfTheTableReturnsTheSameShare() {
        BigDecimal expected = BigDecimal.valueOf(36).divide(BigDecimal.valueOf(37), 6,
                java.math.RoundingMode.HALF_UP);
        for (BetType type : BetType.values()) {
            for (int choice = 0; choice < type.choices(); choice++) {
                Bet bet = new Bet(type, choice);
                assertEquals(0, RouletteWheel.returnRate(bet).compareTo(expected),
                        "Return of " + type + " " + choice + " was " + RouletteWheel.returnRate(bet));
            }
        }
    }

    @Test
    void outsideBetsNeverWinOnZero() {
        for (BetType type : BetType.values()) {
            if (type == BetType.STRAIGHT) continue;
            for (int choice = 0; choice < type.choices(); choice++) {
                assertFalse(RouletteWheel.wins(new Bet(type, choice), 0), type + " must lose on zero");
            }
        }
        assertTrue(RouletteWheel.wins(new Bet(BetType.STRAIGHT, 0), 0), "A bet on zero wins on zero");
    }

    @Test
    void eachBetCoversTheNumbersItShould() {
        assertEquals(1, RouletteWheel.pocketsCovered(new Bet(BetType.STRAIGHT, 17)));
        assertEquals(18, RouletteWheel.pocketsCovered(new Bet(BetType.RED, 0)));
        assertEquals(18, RouletteWheel.pocketsCovered(new Bet(BetType.EVEN, 0)));
        assertEquals(18, RouletteWheel.pocketsCovered(new Bet(BetType.LOW, 0)));
        assertEquals(12, RouletteWheel.pocketsCovered(new Bet(BetType.DOZEN, 0)));
        assertEquals(12, RouletteWheel.pocketsCovered(new Bet(BetType.COLUMN, 2)));
        assertTrue(RouletteWheel.wins(new Bet(BetType.DOZEN, 0), 12), "The first dozen ends at twelve");
        assertFalse(RouletteWheel.wins(new Bet(BetType.DOZEN, 0), 13));
        assertTrue(RouletteWheel.wins(new Bet(BetType.COLUMN, 0), 34), "The first column holds 1, 4, 7 …");
        assertFalse(RouletteWheel.wins(new Bet(BetType.COLUMN, 0), 35));
    }

    @Test
    void payoutsIncludeTheStake() {
        assertEquals(360, RouletteWheel.payout(10, new Bet(BetType.STRAIGHT, 17), 17), "A number pays 36 times");
        assertEquals(0, RouletteWheel.payout(10, new Bet(BetType.STRAIGHT, 17), 18));
        assertEquals(20, RouletteWheel.payout(10, new Bet(BetType.RED, 0), 1));
        assertEquals(30, RouletteWheel.payout(10, new Bet(BetType.DOZEN, 1), 13), "A dozen pays three times");
        assertThrows(IllegalArgumentException.class, () -> RouletteWheel.payout(0, new Bet(BetType.RED, 0), 1));
    }

    @Test
    void theSpinCoversEveryPocketAndNothingElse() {
        long bound = 37_000;
        int[] counted = new int[RouletteWheel.POCKETS];
        for (long draw = 0; draw < bound; draw++) counted[RouletteWheel.spin(draw, bound)]++;
        for (int number = 0; number < RouletteWheel.POCKETS; number++) {
            assertEquals(1_000, counted[number], "Number " + number + " must come out as often as any other");
        }
        assertThrows(IllegalArgumentException.class, () -> RouletteWheel.spin(bound, bound));
        assertThrows(IllegalArgumentException.class, () -> RouletteWheel.spin(-1, bound));
    }

    @Test
    void refusesBetsThatTheTableDoesNotHold() {
        assertThrows(IllegalArgumentException.class, () -> new Bet(BetType.STRAIGHT, RouletteWheel.POCKETS));
        assertThrows(IllegalArgumentException.class, () -> new Bet(BetType.DOZEN, 3));
        assertThrows(IllegalArgumentException.class, () -> new Bet(BetType.RED, 1));
        assertThrows(IllegalArgumentException.class, () -> new Bet(null, 0));
        assertThrows(IllegalArgumentException.class, () -> BetType.fromId("orphelins"));
        assertEquals(BetType.COLUMN, BetType.fromId("column"));
    }
}
