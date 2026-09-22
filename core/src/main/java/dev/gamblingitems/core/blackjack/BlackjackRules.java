package dev.gamblingitems.core.blackjack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure rules of blackjack: what a hand is worth, when the dealer must draw, and what a hand pays.
 * The shuffling, the seats and the payments belong to the server integration.
 *
 * <p>The dealer stands on every seventeen, a natural pays three to two, and a tie returns the stake.
 * Payouts include the stake, as every other game of this mod announces them.
 */
public final class BlackjackRules {
    public static final int CARDS = 52, RANKS = 13, BLACKJACK = 21, DEALER_STANDS = 17;
    /** A natural pays three to two, so the whole payout is five halves of the stake. */
    public static final int NATURAL_NUMERATOR = 5, NATURAL_DENOMINATOR = 2;

    private BlackjackRules() {}

    /** The outcome of a settled hand, as the screen names it. */
    public enum Outcome { PLAYING, NATURAL, WIN, PUSH, LOSS }

    /** What one card is worth. An ace counts eleven when the hand can afford it. */
    public static int valueOf(int card) {
        int rank = rankOf(card);
        if (rank == 0) return 11;
        return Math.min(10, rank + 1);
    }

    public static int rankOf(int card) {
        if (card < 0 || card >= CARDS) throw new IllegalArgumentException("Card outside the deck");
        return card % RANKS;
    }

    public static int suitOf(int card) {
        if (card < 0 || card >= CARDS) throw new IllegalArgumentException("Card outside the deck");
        return card / RANKS;
    }

    /** The best total a hand can hold without busting, or its smallest total once it busts. */
    public static int total(List<Integer> cards) {
        Objects.requireNonNull(cards, "cards");
        int total = 0;
        int aces = 0;
        for (int card : cards) {
            int value = valueOf(card);
            total += value;
            if (value == 11) aces++;
        }
        while (total > BLACKJACK && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    /** True when an ace still counts eleven in this hand. */
    public static boolean isSoft(List<Integer> cards) {
        int hard = 0;
        boolean ace = false;
        for (int card : cards) {
            int value = valueOf(card);
            hard += value == 11 ? 1 : value;
            ace |= value == 11;
        }
        return ace && hard + 10 <= BLACKJACK;
    }

    public static boolean isBust(List<Integer> cards) { return total(cards) > BLACKJACK; }

    /** A natural: twenty one on the first two cards, and nothing else. */
    public static boolean isNatural(List<Integer> cards) {
        return cards.size() == 2 && total(cards) == BLACKJACK;
    }

    /** The dealer draws to sixteen and stands on every seventeen, soft ones included. */
    public static boolean dealerDraws(List<Integer> cards) {
        return total(cards) < DEALER_STANDS;
    }

    /** What a finished hand is worth against the dealer. */
    public static Outcome outcome(List<Integer> player, List<Integer> dealer) {
        if (isBust(player)) return Outcome.LOSS;
        boolean playerNatural = isNatural(player);
        boolean dealerNatural = isNatural(dealer);
        if (playerNatural && dealerNatural) return Outcome.PUSH;
        if (playerNatural) return Outcome.NATURAL;
        if (dealerNatural) return Outcome.LOSS;
        if (isBust(dealer)) return Outcome.WIN;
        int playerTotal = total(player);
        int dealerTotal = total(dealer);
        if (playerTotal > dealerTotal) return Outcome.WIN;
        if (playerTotal < dealerTotal) return Outcome.LOSS;
        return Outcome.PUSH;
    }

    /** Everything the player receives, stake included, rounded down as announced. */
    public static long payout(long stake, Outcome outcome) {
        if (stake <= 0) throw new IllegalArgumentException("A hand needs a positive stake");
        return switch (outcome) {
            case NATURAL -> stake * NATURAL_NUMERATOR / NATURAL_DENOMINATOR;
            case WIN -> Math.multiplyExact(stake, 2L);
            case PUSH -> stake;
            case LOSS, PLAYING -> 0;
        };
    }

    /** The largest payout a stake could win here, used to check it could be handed over. */
    public static long maximumPayout(long stake) {
        // A doubled hand pays twice a doubled stake; a natural cannot be doubled.
        return Math.multiplyExact(stake, 4L);
    }

    /** A fresh deck, shuffled with draws the caller provides. Nothing here touches randomness. */
    public static List<Integer> shuffle(java.util.function.IntUnaryOperator draw) {
        List<Integer> deck = new ArrayList<>(CARDS);
        for (int card = 0; card < CARDS; card++) deck.add(card);
        for (int index = CARDS - 1; index > 0; index--) {
            int swap = draw.applyAsInt(index + 1);
            if (swap < 0 || swap > index) throw new IllegalArgumentException("A draw left the deck");
            int kept = deck.get(index);
            deck.set(index, deck.get(swap));
            deck.set(swap, kept);
        }
        return deck;
    }
}
