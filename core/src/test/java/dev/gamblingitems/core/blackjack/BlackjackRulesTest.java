package dev.gamblingitems.core.blackjack;

import dev.gamblingitems.core.blackjack.BlackjackRules.Outcome;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlackjackRulesTest {
    /** Cards are 0..51; rank 0 is an ace, rank 9 a ten, ranks 10 to 12 the figures. */
    private static int card(int rank, int suit) { return suit * BlackjackRules.RANKS + rank; }

    private static List<Integer> hand(int... ranks) {
        List<Integer> cards = new java.util.ArrayList<>();
        for (int index = 0; index < ranks.length; index++) cards.add(card(ranks[index], index % 4));
        return cards;
    }

    @Test
    void cardsAreWorthWhatTheFeltSays() {
        assertEquals(11, BlackjackRules.valueOf(card(0, 0)), "An ace starts at eleven");
        assertEquals(2, BlackjackRules.valueOf(card(1, 0)));
        assertEquals(10, BlackjackRules.valueOf(card(9, 2)), "A ten");
        assertEquals(10, BlackjackRules.valueOf(card(10, 1)), "A jack");
        assertEquals(10, BlackjackRules.valueOf(card(12, 3)), "A king");
        assertThrows(IllegalArgumentException.class, () -> BlackjackRules.valueOf(BlackjackRules.CARDS));
    }

    @Test
    void acesFallBackToOneOnlyWhenTheyMust() {
        assertEquals(21, BlackjackRules.total(hand(0, 9)), "Ace and ten");
        assertTrue(BlackjackRules.isSoft(hand(0, 5)), "Ace and six is soft seventeen");
        assertEquals(17, BlackjackRules.total(hand(0, 5)));
        assertEquals(13, BlackjackRules.total(hand(0, 9, 1)), "The ace drops to one");
        assertFalse(BlackjackRules.isSoft(hand(0, 9, 1)));
        assertEquals(12, BlackjackRules.total(hand(0, 0)), "Two aces are eleven and one");
        assertEquals(13, BlackjackRules.total(hand(0, 0, 0)));
    }

    @Test
    void aNaturalIsTwentyOneOnTwoCardsOnly() {
        assertTrue(BlackjackRules.isNatural(hand(0, 12)));
        assertFalse(BlackjackRules.isNatural(hand(9, 5, 4)), "Twenty one on three cards is not a natural");
        assertEquals(21, BlackjackRules.total(hand(9, 5, 4)));
        assertTrue(BlackjackRules.isBust(hand(9, 9, 9)));
        assertEquals(30, BlackjackRules.total(hand(9, 9, 9)));
    }

    @Test
    void theDealerDrawsToSixteenAndStandsOnEverySeventeen() {
        assertTrue(BlackjackRules.dealerDraws(hand(9, 5)), "Sixteen draws");
        assertFalse(BlackjackRules.dealerDraws(hand(9, 6)), "Seventeen stands");
        assertFalse(BlackjackRules.dealerDraws(hand(0, 5)), "A soft seventeen stands too");
        assertTrue(BlackjackRules.dealerDraws(hand(0, 4)), "A soft sixteen draws");
    }

    @Test
    void everyOutcomeOfTheTable() {
        assertEquals(Outcome.NATURAL, BlackjackRules.outcome(hand(0, 12), hand(9, 6)));
        assertEquals(Outcome.PUSH, BlackjackRules.outcome(hand(0, 12), hand(0, 9)), "Two naturals tie");
        assertEquals(Outcome.LOSS, BlackjackRules.outcome(hand(9, 6), hand(0, 12)), "A dealer natural wins");
        assertEquals(Outcome.WIN, BlackjackRules.outcome(hand(9, 8), hand(9, 6)), "Nineteen beats seventeen");
        assertEquals(Outcome.LOSS, BlackjackRules.outcome(hand(9, 4), hand(9, 8)));
        assertEquals(Outcome.PUSH, BlackjackRules.outcome(hand(9, 8), hand(9, 8)));
        assertEquals(Outcome.WIN, BlackjackRules.outcome(hand(9, 6), hand(9, 9, 9)), "A busted dealer pays");
        assertEquals(Outcome.LOSS, BlackjackRules.outcome(hand(9, 9, 9), hand(9, 9, 9)),
                "A busted hand loses even against a busted dealer");
    }

    @Test
    void payoutsIncludeTheStakeAndRoundDown() {
        assertEquals(25, BlackjackRules.payout(10, Outcome.NATURAL), "Three to two");
        assertEquals(7, BlackjackRules.payout(3, Outcome.NATURAL), "7.5 is paid as 7");
        assertEquals(20, BlackjackRules.payout(10, Outcome.WIN));
        assertEquals(10, BlackjackRules.payout(10, Outcome.PUSH), "A tie returns the stake");
        assertEquals(0, BlackjackRules.payout(10, Outcome.LOSS));
        assertEquals(40, BlackjackRules.maximumPayout(10), "A doubled hand is the largest win");
        assertThrows(IllegalArgumentException.class, () -> BlackjackRules.payout(0, Outcome.WIN));
    }

    @Test
    void aShuffleKeepsEveryCardExactlyOnce() {
        java.util.Random random = new java.util.Random(7);
        List<Integer> deck = BlackjackRules.shuffle(random::nextInt);
        assertEquals(BlackjackRules.CARDS, deck.size());
        assertEquals(BlackjackRules.CARDS, new java.util.HashSet<>(deck).size(), "No card is lost or doubled");
        assertThrows(IllegalArgumentException.class, () -> BlackjackRules.shuffle(bound -> bound));
    }

    @Test
    void theDeckIsOnlyShuffledByTheDrawsItIsGiven() {
        // The same draws always produce the same deck, so a hand can be replayed from its seed.
        List<Integer> first = BlackjackRules.shuffle(new java.util.Random(11)::nextInt);
        List<Integer> second = BlackjackRules.shuffle(new java.util.Random(11)::nextInt);
        assertEquals(first, second);
    }
}
