package dev.gamblingitems.core.machines;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static dev.gamblingitems.core.machines.VideoPokerRules.*;
import static org.junit.jupiter.api.Assertions.*;

class VideoPokerRulesTest {
    /** A card from its rank (2 to 14, the ace) and its suit (0 to 3). */
    private static int card(int rank, int suit) { return suit * RANKS + rank - 2; }

    private static int[] hand(int... rankSuits) {
        int[] hand = new int[HAND];
        for (int index = 0; index < HAND; index++) hand[index] = card(rankSuits[index * 2], rankSuits[index * 2 + 1]);
        return hand;
    }

    @Test
    void everyHandIsNamedByItsBestCombination() {
        assertEquals(ROYAL_FLUSH, evaluate(hand(10, 1, 11, 1, 12, 1, 13, 1, 14, 1)));
        assertEquals(STRAIGHT_FLUSH, evaluate(hand(14, 2, 2, 2, 3, 2, 4, 2, 5, 2)), "The ace plays low too");
        assertEquals(FOUR_OF_A_KIND, evaluate(hand(9, 0, 9, 1, 9, 2, 9, 3, 2, 0)));
        assertEquals(FULL_HOUSE, evaluate(hand(9, 0, 9, 1, 9, 2, 4, 3, 4, 0)));
        assertEquals(FLUSH, evaluate(hand(2, 3, 7, 3, 9, 3, 11, 3, 13, 3)));
        assertEquals(STRAIGHT, evaluate(hand(6, 0, 7, 1, 8, 2, 9, 3, 10, 0)));
        assertEquals(STRAIGHT, evaluate(hand(10, 0, 11, 1, 12, 2, 13, 3, 14, 0)), "Ace high");
        assertEquals(NOTHING, evaluate(hand(12, 0, 13, 1, 14, 2, 2, 3, 3, 0)), "No turning the corner");
        assertEquals(THREE_OF_A_KIND, evaluate(hand(5, 0, 5, 1, 5, 2, 9, 3, 12, 0)));
        assertEquals(TWO_PAIR, evaluate(hand(5, 0, 5, 1, 3, 2, 3, 3, 12, 0)));
        assertEquals(JACKS_OR_BETTER, evaluate(hand(11, 0, 11, 1, 3, 2, 6, 3, 12, 0)));
        assertEquals(NOTHING, evaluate(hand(10, 0, 10, 1, 3, 2, 6, 3, 12, 0)), "A pair of tens pays nothing");
        assertEquals(2500, multiplier(PAYTABLE, hand(9, 0, 9, 1, 9, 2, 9, 3, 2, 0)));
    }

    @Test
    void heldCardsStayAndTheOthersComeFromTheRestOfTheDeck() {
        Random random = new Random(7);
        for (int round = 0; round < 2_000; round++) {
            int[] first = deal(random::nextInt);
            int held = random.nextInt(32);
            int[] last = VideoPokerRules.draw(first, held, random::nextInt);
            Set<Integer> seen = new HashSet<>();
            for (int index = 0; index < HAND; index++) {
                assertTrue(seen.add(last[index]), "A card is in the hand once");
                if ((held & 1 << index) != 0) {
                    assertEquals(first[index], last[index], "A held card stays");
                } else {
                    for (int card : first) assertNotEquals(card, last[index], "A card let go never comes back");
                }
            }
        }
    }

    @Test
    void aDealIsFiveDifferentCards() {
        assertArrayEquals(new int[] {0, 1, 2, 3, 4}, deal(bound -> 0));
        assertThrows(IllegalArgumentException.class, () -> deal(bound -> bound));
        assertThrows(IllegalArgumentException.class, () -> evaluate(new int[] {1, 1, 2, 3, 4}));
    }
}
