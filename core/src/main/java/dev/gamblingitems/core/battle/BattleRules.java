package dev.gamblingitems.core.battle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure rules of a case battle: who may enter, and who takes everything at the end.
 * The cases, the draws and the payments belong to the server integration.
 *
 * <p>The score of a seat is the value of what it opened, frozen with the catalogue of the battle.
 * The best score takes every item opened in the battle; a tie is settled by a uniform draw between
 * the tied seats, announced before anyone enters.
 */
public final class BattleRules {
    public static final int MIN_PLAYERS = 2, MAX_PLAYERS = 4;
    public static final int MIN_ROUNDS = 1, MAX_ROUNDS = 16;

    private BattleRules() {}

    public static boolean acceptsPlayers(int players) {
        return players >= MIN_PLAYERS && players <= MAX_PLAYERS;
    }

    public static boolean acceptsRounds(int rounds) {
        return rounds >= MIN_ROUNDS && rounds <= MAX_ROUNDS;
    }

    /** Seats that hold the best score, in seat order. Never empty when there is a seat. */
    public static List<Integer> leaders(List<Long> scores) {
        Objects.requireNonNull(scores, "scores");
        if (scores.isEmpty()) throw new IllegalArgumentException("A battle needs seats");
        long best = Long.MIN_VALUE;
        for (long score : scores) best = Math.max(best, score);
        List<Integer> leaders = new ArrayList<>();
        for (int seat = 0; seat < scores.size(); seat++) {
            if (scores.get(seat) == best) leaders.add(seat);
        }
        return leaders;
    }

    /**
     * The seat that takes everything. The caller supplies an independent server-side draw in
     * [0, bound); it only ever decides between seats that are exactly tied.
     */
    public static int winner(List<Long> scores, long draw, long bound) {
        List<Integer> leaders = leaders(scores);
        if (leaders.size() == 1) return leaders.get(0);
        if (bound < 1 || draw < 0 || draw >= bound) throw new IllegalArgumentException("draw is outside the tie");
        return leaders.get((int) (draw * leaders.size() / bound));
    }
}
