package dev.gamblingitems.core.machines;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/**
 * Jacks or better, the classic video poker: five cards from one 52-card deck, the player keeps any
 * of them, the others are replaced once from the rest of the same deck, and the final hand is paid
 * by the paytable.
 *
 * <p>A card is a number from 0 to 51: its rank is {@code card % 13}, 0 for a two up to 12 for an
 * ace, and its suit {@code card / 13}.
 */
public final class VideoPokerRules {
    public static final int HAND = 5, DECK = 52, RANKS = 13;
    /** Paid hands, from the lowest; {@link #NOTHING} pays nothing. */
    public static final int NOTHING = -1, JACKS_OR_BETTER = 0, TWO_PAIR = 1, THREE_OF_A_KIND = 2, STRAIGHT = 3,
            FLUSH = 4, FULL_HOUSE = 5, FOUR_OF_A_KIND = 6, STRAIGHT_FLUSH = 7, ROYAL_FLUSH = 8;
    /** The "9/6" paytable, stake included, in hundredths: the royal flush at its five-coin rate of 800. */
    public static final int[] PAYTABLE = {100, 200, 300, 400, 600, 900, 2500, 5000, 80000};
    /** What that paytable gives back with the best holds, as computed for the game since the 1980s. */
    public static final BigDecimal STANDARD_RETURN = new BigDecimal("0.995439");
    private static final int JACK = 9, ACE = 12;

    private VideoPokerRules() {}

    public static int rank(int card) { return card % RANKS; }
    public static int suit(int card) { return card / RANKS; }

    /** Five different cards from a full deck. {@code draw} answers a number in [0, bound). */
    public static int[] deal(IntUnaryOperator draw) {
        return pick(new int[0], HAND, draw);
    }

    /**
     * The final hand: the held cards stay, each other one is replaced by a card of the deck that was
     * never in the first hand. Bit i of {@code held} keeps card i.
     */
    public static int[] draw(int[] hand, int held, IntUnaryOperator draw) {
        requireHand(hand);
        int replaced = HAND - Integer.bitCount(held & 0b11111);
        int[] fresh = pick(hand, replaced, draw);
        int[] result = hand.clone();
        for (int index = 0, next = 0; index < HAND; index++) {
            if ((held & (1 << index)) == 0) result[index] = fresh[next++];
        }
        return result;
    }

    /** {@code count} different cards from the deck, none of them among {@code excluded}. */
    private static int[] pick(int[] excluded, int count, IntUnaryOperator draw) {
        int[] deck = new int[DECK - excluded.length];
        for (int card = 0, at = 0; card < DECK; card++) {
            final int candidate = card;
            if (Arrays.stream(excluded).noneMatch(value -> value == candidate)) deck[at++] = card;
        }
        int[] picked = new int[count];
        for (int index = 0; index < count; index++) {
            int left = deck.length - index;
            int chosen = draw.applyAsInt(left);
            if (chosen < 0 || chosen >= left) throw new IllegalArgumentException("Draw outside [0, " + left + "): " + chosen);
            picked[index] = deck[index + chosen];
            deck[index + chosen] = deck[index];
            deck[index] = picked[index];
        }
        return picked;
    }

    /** The best paid hand among these five cards, or {@link #NOTHING}. */
    public static int evaluate(int[] hand) {
        requireHand(hand);
        int[] counts = new int[RANKS];
        boolean flush = true;
        for (int card : hand) {
            counts[rank(card)]++;
            flush &= suit(card) == suit(hand[0]);
        }
        int pairs = 0, three = 0, four = 0, highPair = 0;
        for (int rank = 0; rank < RANKS; rank++) {
            if (counts[rank] == 2) {
                pairs++;
                if (rank >= JACK) highPair++;
            }
            if (counts[rank] == 3) three++;
            if (counts[rank] == 4) four++;
        }
        boolean distinct = pairs == 0 && three == 0 && four == 0;
        int low = RANKS, high = -1;
        for (int rank = 0; rank < RANKS; rank++) {
            if (counts[rank] == 0) continue;
            low = Math.min(low, rank);
            high = Math.max(high, rank);
        }
        // The ace also plays low, under the two: A-2-3-4-5.
        boolean wheel = distinct && counts[ACE] == 1 && counts[0] == 1 && counts[1] == 1 && counts[2] == 1 && counts[3] == 1;
        boolean straight = distinct && (high - low == 4 || wheel);
        if (straight && flush) return low == 8 ? ROYAL_FLUSH : STRAIGHT_FLUSH;
        if (four == 1) return FOUR_OF_A_KIND;
        if (three == 1 && pairs == 1) return FULL_HOUSE;
        if (flush) return FLUSH;
        if (straight) return STRAIGHT;
        if (three == 1) return THREE_OF_A_KIND;
        if (pairs == 2) return TWO_PAIR;
        if (highPair == 1) return JACKS_OR_BETTER;
        return NOTHING;
    }

    /** What a hand pays with this paytable, in hundredths of the stake, stake included. */
    public static int multiplier(int[] paytable, int[] hand) {
        int paid = evaluate(hand);
        return paid == NOTHING ? 0 : paytable[paid];
    }

    private static void requireHand(int[] hand) {
        if (hand == null || hand.length != HAND) throw new IllegalArgumentException("A hand has five cards");
        for (int index = 0; index < HAND; index++) {
            if (hand[index] < 0 || hand[index] >= DECK) throw new IllegalArgumentException("Unknown card " + hand[index]);
            for (int other = 0; other < index; other++) {
                if (hand[other] == hand[index]) throw new IllegalArgumentException("A card is dealt only once");
            }
        }
    }
}
