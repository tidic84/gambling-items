package dev.gamblingitems.core.bingo;

import java.math.BigDecimal;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BingoRulesTest {
    private static boolean[] drawn(int... numbers) {
        boolean[] drawn = new boolean[BingoRules.NUMBERS + 1];
        for (int number : numbers) drawn[number] = true;
        return drawn;
    }

    @Test
    void everyColumnTakesItsOwnNumbers() {
        assertEquals(0, BingoRules.columnOf(1));
        assertEquals(0, BingoRules.columnOf(15));
        assertEquals(1, BingoRules.columnOf(16));
        assertEquals(4, BingoRules.columnOf(BingoRules.NUMBERS));
        assertThrows(IllegalArgumentException.class, () -> BingoRules.columnOf(0));
        assertThrows(IllegalArgumentException.class, () -> BingoRules.columnOf(BingoRules.NUMBERS + 1));
    }

    @Test
    void aCardIsWellFormedAndRepeatable() {
        int[] first = BingoRules.card(new Random(4)::nextInt);
        int[] second = BingoRules.card(new Random(4)::nextInt);
        assertArrayEquals(first, second, "The same draws build the same card");
        assertTrue(BingoRules.isCard(first));
        assertEquals(0, first[BingoRules.FREE_SQUARE], "The middle square is free");
        assertThrows(IllegalArgumentException.class, () -> BingoRules.card(size -> size));
    }

    @Test
    void aBrokenCardIsRefused() {
        int[] card = BingoRules.card(new Random(9)::nextInt);
        assertTrue(BingoRules.isCard(card));
        int[] wrongColumn = card.clone();
        wrongColumn[0] = BingoRules.NUMBERS;
        assertFalse(BingoRules.isCard(wrongColumn), "A number must sit in its own column");
        int[] repeated = card.clone();
        repeated[1] = repeated[6];
        assertFalse(BingoRules.isCard(repeated), "A number never appears twice");
        assertFalse(BingoRules.isCard(new int[BingoRules.SIZE]), "An empty card is not a card");
        assertFalse(BingoRules.isCard(null));
    }

    @Test
    void theFreeSquareIsMarkedFromTheStart() {
        int[] card = BingoRules.card(new Random(1)::nextInt);
        boolean[] marks = BingoRules.marks(card, drawn());
        assertTrue(marks[BingoRules.FREE_SQUARE]);
        for (int square = 0; square < BingoRules.SIZE; square++) {
            if (square != BingoRules.FREE_SQUARE) assertFalse(marks[square], "Nothing else is marked yet");
        }
        assertFalse(BingoRules.hasLine(marks), "One free square is not a line");
    }

    @Test
    void aFullRowColumnOrDiagonalWins() {
        int[] card = BingoRules.card(new Random(3)::nextInt);
        // The middle row, which crosses the free square.
        boolean[] drawn = drawn(card[10], card[11], card[13], card[14]);
        assertTrue(BingoRules.hasLine(BingoRules.marks(card, drawn)), "A row across the free square wins");
        boolean[] column = drawn(card[0], card[5], card[10], card[15], card[20]);
        assertTrue(BingoRules.hasLine(BingoRules.marks(card, column)), "A column wins");
        boolean[] diagonal = drawn(card[0], card[6], card[18], card[24]);
        assertTrue(BingoRules.hasLine(BingoRules.marks(card, diagonal)), "A diagonal through the free square wins");
        boolean[] almost = drawn(card[0], card[5], card[10], card[15]);
        assertFalse(BingoRules.hasLine(BingoRules.marks(card, almost)), "Four of five is not a line");
        assertEquals(1, BingoRules.missing(BingoRules.marks(card, almost)), "One square away");
    }

    @Test
    void aPotIsSharedBetweenTheCardsThatFilledALine() {
        BigDecimal rate = new BigDecimal("0.90");
        assertEquals(90, BingoRules.share(100, 1, rate), "One winner takes the announced share");
        assertEquals(45, BingoRules.share(100, 2, rate), "Two winners split it");
        assertEquals(30, BingoRules.share(100, 3, rate), "Rounded down, as announced");
        assertThrows(IllegalArgumentException.class, () -> BingoRules.share(0, 1, rate));
        assertThrows(IllegalArgumentException.class, () -> BingoRules.share(100, 0, rate));
        assertThrows(IllegalArgumentException.class, () -> BingoRules.share(100, 1, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> BingoRules.share(100, 1, new BigDecimal("1.5")));
    }

    @Test
    void everyNumberOfTheDrumCanBeDrawn() {
        // A round draws without replacement: every number must be reachable and none twice.
        boolean[] seen = new boolean[BingoRules.NUMBERS + 1];
        Random random = new Random(12);
        java.util.List<Integer> drum = new java.util.ArrayList<>();
        for (int number = 1; number <= BingoRules.NUMBERS; number++) drum.add(number);
        while (!drum.isEmpty()) {
            int number = drum.remove(random.nextInt(drum.size()));
            assertFalse(seen[number], "A number is drawn once");
            seen[number] = true;
        }
        for (int number = 1; number <= BingoRules.NUMBERS; number++) assertTrue(seen[number]);
    }
}
