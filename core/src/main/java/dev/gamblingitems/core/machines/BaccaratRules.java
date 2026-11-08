package dev.gamblingitems.core.machines;

import java.util.SplittableRandom;

/**
 * Punto banco, the baccarat of every casino floor, dealt from a shoe of eight decks. The player and the
 * banker get two cards each, counted modulo ten (tens and faces count 0, an ace 1). A total of 8 or 9
 * is a natural and nobody draws. Otherwise the player draws a third card on 0 to 5, and the banker
 * follows the printed tableau, which depends on the player's third card.
 *
 * <p>A card is a rank from 0 (ace) to 12 (king). The cards are dealt player, banker, player, banker,
 * then the third cards in turn.
 */
public final class BaccaratRules {
    public static final int PLAYER = 0, BANKER = 1, TIE = 2;
    public static final int DECKS = 8, RANKS = 13, SHOE = DECKS * 52;
    /** The chance of each result in a billion, from every way six cards can leave the shoe. */
    public static final int[] EVENTS = exactEvents();

    /** A dealt coup: the cards of each side, in the order they are turned. */
    public record Coup(int[] player, int[] banker) {
        public int playerTotal() { return total(player); }
        public int bankerTotal() { return total(banker); }

        public int result() {
            int player = playerTotal(), banker = bankerTotal();
            return player > banker ? PLAYER : banker > player ? BANKER : TIE;
        }
    }

    private BaccaratRules() {}

    public static int value(int rank) { return rank >= 9 ? 0 : rank + 1; }

    private static int total(int[] cards) {
        int total = 0;
        for (int card : cards) total += value(card);
        return total % 10;
    }

    /** Deals a coup from the first cards of {@code cards}: six ranks, of which four to six are used. */
    public static Coup coup(int[] cards) {
        int player = (value(cards[0]) + value(cards[2])) % 10, banker = (value(cards[1]) + value(cards[3])) % 10;
        if (player >= 8 || banker >= 8) return new Coup(new int[] {cards[0], cards[2]}, new int[] {cards[1], cards[3]});
        int next = 4;
        int third = -1;
        if (player <= 5) third = cards[next++];
        boolean bankerDraws = third < 0 ? banker <= 5 : banker <= 2
                || banker == 3 && value(third) != 8
                || banker == 4 && value(third) >= 2 && value(third) <= 7
                || banker == 5 && value(third) >= 4 && value(third) <= 7
                || banker == 6 && value(third) >= 6 && value(third) <= 7;
        int[] playerCards = third < 0 ? new int[] {cards[0], cards[2]} : new int[] {cards[0], cards[2], third};
        int[] bankerCards = bankerDraws ? new int[] {cards[1], cards[3], cards[next]} : new int[] {cards[1], cards[3]};
        return new Coup(playerCards, bankerCards);
    }

    /** A coup from a fresh shoe that ends with {@code result}, the same for every viewer of the same seed. */
    public static Coup deal(int result, int seed) {
        if (result < 0 || result > TIE) throw new IllegalArgumentException("Unknown baccarat result " + result);
        SplittableRandom random = new SplittableRandom(seed);
        while (true) {
            int[] left = new int[RANKS];
            java.util.Arrays.fill(left, DECKS * 4);
            int[] cards = new int[6];
            for (int index = 0; index < cards.length; index++) {
                int ticket = random.nextInt(SHOE - index), rank = 0;
                for (int seen = left[0]; ticket >= seen; seen += left[++rank]) {}
                left[rank]--;
                cards[index] = rank;
            }
            Coup coup = coup(cards);
            if (coup.result() == result) return coup;
        }
    }

    /**
     * Counts every ordered draw of six cards by value, each weighted by how many cards of the shoe
     * have it; the cards a coup does not use are counted too, which weighs every coup alike.
     */
    private static int[] exactEvents() {
        long[] cards = new long[10];
        cards[0] = 16L * DECKS;
        for (int value = 1; value < 10; value++) cards[value] = 4L * DECKS;
        long[] counts = new long[3];
        int[] values = new int[6];
        count(cards, values, 0, 1, counts);
        long total = counts[0] + counts[1] + counts[2];
        int[] events = new int[3];
        for (int result = 0; result < 3; result++) events[result] = (int) Math.round(counts[result] * 1e9 / total);
        return events;
    }

    private static void count(long[] cards, int[] values, int depth, long ways, long[] counts) {
        if (depth == values.length) {
            // Ranks stand in for values here: rank v - 1 counts v, and rank 9 counts 0.
            int[] ranks = new int[6];
            for (int index = 0; index < 6; index++) ranks[index] = values[index] == 0 ? 9 : values[index] - 1;
            counts[coup(ranks).result()] += ways;
            return;
        }
        for (int value = 0; value < 10; value++) {
            if (cards[value] == 0) continue;
            values[depth] = value;
            long weight = cards[value]--;
            count(cards, values, depth + 1, ways * weight, counts);
            cards[value]++;
        }
    }
}
