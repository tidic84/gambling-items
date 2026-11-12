package dev.gamblingitems.fabric.prediction;

import dev.gamblingitems.core.prediction.PredictionMarket.Side;

/**
 * What a question is about. The game itself answers every question about a player; only a question
 * written by hand needs someone to state its answer.
 *
 * <p>The sabotage side is the answer a player could bring about on purpose by failing: dying, or not
 * doing what was asked. A player who bets that side on a question about themselves is a match fixer.
 */
public enum MarketKind {
    /** Will this player earn this advancement in time? */
    ADVANCEMENT("advancement", Side.NO, 1, 1),
    /** Will this player die at least this many times? */
    DEATHS("deaths", Side.YES, 3, 20),
    /** Will this player mine at least this many diamond ores? */
    DIAMONDS("diamonds", Side.NO, 10, 64),
    /** Will this player kill at least this many mobs? */
    MOB_KILLS("mob_kills", Side.NO, 25, 500),
    /** A question written on a named paper, answered by its creator and open to dispute. */
    CUSTOM("custom", null, 0, 0);

    private final String id;
    private final Side sabotage;
    private final int defaultThreshold, maximumThreshold;

    MarketKind(String id, Side sabotage, int defaultThreshold, int maximumThreshold) {
        this.id = id;
        this.sabotage = sabotage;
        this.defaultThreshold = defaultThreshold;
        this.maximumThreshold = maximumThreshold;
    }

    public String id() { return id; }
    public boolean aboutPlayer() { return this != CUSTOM; }
    /** True when the question counts something, so it takes a threshold. */
    public boolean counted() { return this == DEATHS || this == DIAMONDS || this == MOB_KILLS; }
    public Side sabotage() { return sabotage; }
    public int defaultThreshold() { return defaultThreshold; }
    public int maximumThreshold() { return maximumThreshold; }

    public static MarketKind fromId(String id) {
        for (MarketKind kind : values()) if (kind.id.equals(id)) return kind;
        throw new IllegalArgumentException("Unknown market kind: " + id);
    }
}
