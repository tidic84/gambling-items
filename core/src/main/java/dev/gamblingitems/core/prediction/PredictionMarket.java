package dev.gamblingitems.core.prediction;

import java.util.Objects;

/**
 * One yes or no question traded against an {@link Lmsr} maker, from its opening to the last ticket
 * cashed. Nothing here knows Minecraft: values are counted in the units of the item catalogue, shares
 * in thousandths so a saved market never drifts, and time is whatever clock the caller passes.
 *
 * <p>The money of a market is its pool: the creator's subsidy, plus every purchase, minus every sale
 * and every payout. The maker's rule guarantees the pool always covers the winning side, and every
 * rounding goes in the pool's favour, so a ticket can always be paid in full.
 *
 * <h2>Life of a question</h2>
 * <pre>
 *   OPEN ──close──▶ CLOSED ──propose──▶ PROPOSED ──challenge window ends──▶ RESOLVED
 *     │                │                    └──dispute──▶ DISPUTED ──resolve──▶ RESOLVED
 *     └────────────────┴──resolve (the game saw it happen)──────────────────▶ RESOLVED
 *   any unfinished state ──cancel──▶ CANCELLED (tickets repaid at the last price)
 * </pre>
 */
public final class PredictionMarket {
    /** Shares are stored in thousandths. */
    public static final long MILLI = 1_000;
    public static final int BASIS = 10_000;

    public enum Side {
        YES("yes"), NO("no");

        private final String id;
        Side(String id) { this.id = id; }
        public String id() { return id; }
        public Side opposite() { return this == YES ? NO : YES; }
        public boolean yes() { return this == YES; }

        public static Side fromId(String id) {
            for (Side side : values()) if (side.id.equals(id)) return side;
            throw new IllegalArgumentException("Unknown side: " + id);
        }
    }

    public enum Status {
        OPEN("open"), CLOSED("closed"), PROPOSED("proposed"), DISPUTED("disputed"),
        RESOLVED("resolved"), CANCELLED("cancelled");

        private final String id;
        Status(String id) { this.id = id; }
        public String id() { return id; }
        /** Nothing can change a finished market; its tickets can only be cashed. */
        public boolean finished() { return this == RESOLVED || this == CANCELLED; }

        public static Status fromId(String id) {
            for (Status status : values()) if (status.id.equals(id)) return status;
            throw new IllegalArgumentException("Unknown market status: " + id);
        }
    }

    /** What one purchase bought: shares in thousandths, the fee kept for the creator and what joined the pool. */
    public record Purchase(Side side, long shares, long fee, long net) {}

    private final long shareValue;
    private final int feeBasisPoints;
    private final Lmsr maker;
    private long yes, no;
    private long pool, fees, volume;
    private Status status = Status.OPEN;
    private Side outcome, proposal;
    private long challengeEnds;
    /** The price of YES when the market was cancelled, in basis points: tickets are repaid at it. */
    private int cancelPrice = -1;

    private PredictionMarket(long shareValue, int feeBasisPoints, double liquidity, long pool) {
        if (shareValue <= 0) throw new IllegalArgumentException("A share must be worth something");
        if (feeBasisPoints < 0 || feeBasisPoints >= BASIS) throw new IllegalArgumentException("Invalid fee");
        this.shareValue = shareValue;
        this.feeBasisPoints = feeBasisPoints;
        this.maker = new Lmsr(liquidity);
        this.pool = pool;
    }

    /**
     * Opens a question funded by a subsidy. The subsidy is the most the maker can lose: it sets
     * how deep the market is, and what is left of it after the payouts goes back to the creator.
     */
    public static PredictionMarket open(long subsidy, long shareValue, int feeBasisPoints) {
        if (subsidy <= 0) throw new IllegalArgumentException("A market needs a subsidy");
        double liquidity = Lmsr.funded((double) subsidy / shareValue).liquidity();
        return new PredictionMarket(shareValue, feeBasisPoints, liquidity, subsidy);
    }

    /** Rebuilds a saved market exactly as it was. */
    public static PredictionMarket restore(long shareValue, int feeBasisPoints, double liquidity, long yes, long no,
                                           long pool, long fees, long volume, Status status, Side outcome,
                                           Side proposal, long challengeEnds, int cancelPrice) {
        PredictionMarket market = new PredictionMarket(shareValue, feeBasisPoints, liquidity, pool);
        if (yes < 0 || no < 0 || pool < 0 || fees < 0 || volume < 0) throw new IllegalArgumentException("Corrupt market");
        market.yes = yes;
        market.no = no;
        market.fees = fees;
        market.volume = volume;
        market.status = Objects.requireNonNull(status, "status");
        market.outcome = outcome;
        market.proposal = proposal;
        market.challengeEnds = challengeEnds;
        market.cancelPrice = cancelPrice;
        if (status == Status.RESOLVED && outcome == null) throw new IllegalArgumentException("A resolved market needs an outcome");
        if (status == Status.CANCELLED && cancelPrice < 0) throw new IllegalArgumentException("A cancelled market needs its price");
        return market;
    }

    public long shareValue() { return shareValue; }
    public int feeBasisPoints() { return feeBasisPoints; }
    public double liquidity() { return maker.liquidity(); }
    public long yesShares() { return yes; }
    public long noShares() { return no; }
    public long shares(Side side) { return side.yes() ? yes : no; }
    public long pool() { return pool; }
    public long fees() { return fees; }
    public long volume() { return volume; }
    public Status status() { return status; }
    public Side outcome() { return outcome; }
    public Side proposal() { return proposal; }
    public long challengeEnds() { return challengeEnds; }
    public int cancelPrice() { return cancelPrice; }
    public boolean trading() { return status == Status.OPEN; }

    private double shares(long milli) { return (double) milli / MILLI; }

    /** The price of YES in basis points: the probability the traders give to the event. */
    public int priceBasisPoints() {
        if (status == Status.RESOLVED) return outcome.yes() ? BASIS : 0;
        if (status == Status.CANCELLED) return cancelPrice;
        return (int) Math.round(maker.priceYes(shares(yes), shares(no)) * BASIS);
    }

    public int priceBasisPoints(Side side) {
        int yesPrice = priceBasisPoints();
        return side.yes() ? yesPrice : BASIS - yesPrice;
    }

    /** What this spend would buy right now, without buying it; null when it buys nothing. */
    public Purchase quote(Side side, long spend) {
        if (!trading() || spend <= 0) return null;
        long fee = spend * feeBasisPoints / BASIS;
        long net = spend - fee;
        double bought = maker.sharesFor(side.yes(), shares(yes), shares(no), (double) net / shareValue);
        long milli = (long) Math.floor(bought * MILLI);
        if (milli <= 0) return null;
        return new Purchase(side, milli, fee, net);
    }

    /** Buys shares of a side with this spend. The whole spend is taken; null when it buys nothing. */
    public Purchase buy(Side side, long spend) {
        Purchase purchase = quote(side, spend);
        if (purchase == null) return null;
        if (side.yes()) yes += purchase.shares();
        else no += purchase.shares();
        pool += purchase.net();
        fees += purchase.fee();
        volume += spend;
        return purchase;
    }

    /** What selling these shares back would return right now, rounded down. */
    public long quoteSell(Side side, long milli) {
        if (!trading() || milli <= 0 || milli > shares(side)) return 0;
        double value = maker.sellValue(side.yes(), shares(yes), shares(no), shares(milli)) * shareValue;
        return Math.min(pool, (long) Math.floor(value));
    }

    /** Sells shares back to the maker while the market trades; returns what the pool pays for them. */
    public long sell(Side side, long milli) {
        if (!trading()) throw new IllegalStateException("The market no longer trades");
        if (milli <= 0 || milli > shares(side)) throw new IllegalArgumentException("Not that many shares outstanding");
        long value = quoteSell(side, milli);
        if (side.yes()) yes -= milli;
        else no -= milli;
        pool -= value;
        volume += value;
        return value;
    }

    /** Trading stops: the question waits for its answer. */
    public void close() {
        if (status == Status.OPEN) status = Status.CLOSED;
    }

    /** Someone states the answer; anyone may dispute it until the challenge window ends. */
    public void propose(Side answer, long now, long windowMillis) {
        if (status != Status.OPEN && status != Status.CLOSED) throw new IllegalStateException("Nothing to propose");
        proposal = Objects.requireNonNull(answer, "answer");
        challengeEnds = now + Math.max(0, windowMillis);
        status = Status.PROPOSED;
    }

    public void dispute() {
        if (status != Status.PROPOSED) throw new IllegalStateException("Only a proposed answer can be disputed");
        status = Status.DISPUTED;
    }

    /**
     * Settles an undisputed proposal once its window has passed. Returns what {@link #resolve} hands
     * back to the creator, or -1 while the answer can still be disputed.
     */
    public long finishChallenge(long now) {
        if (status != Status.PROPOSED || now < challengeEnds) return -1;
        return resolve(proposal);
    }

    /**
     * Sets the answer for good. What the pool holds beyond what the winning tickets will be paid is
     * returned, for the creator; the pool keeps exactly what it owes.
     */
    public long resolve(Side answer) {
        if (status.finished()) throw new IllegalStateException("The market is already finished");
        outcome = Objects.requireNonNull(answer, "answer");
        status = Status.RESOLVED;
        return release(ceilValue(shares(answer), BASIS));
    }

    /** Calls the market off: every ticket is repaid at the last price it traded at. */
    public long cancel() {
        if (status.finished()) throw new IllegalStateException("The market is already finished");
        cancelPrice = (int) Math.round(maker.priceYes(shares(yes), shares(no)) * BASIS);
        status = Status.CANCELLED;
        return release(ceilValue(yes, cancelPrice) + ceilValue(no, BASIS - cancelPrice));
    }

    /** What these shares are worth at this price, rounded up: what the pool must keep for them. */
    private long ceilValue(long milli, int price) {
        return (long) Math.ceil((double) milli * shareValue * price / ((double) MILLI * BASIS));
    }

    private long release(long owed) {
        long left = Math.max(0, pool - owed);
        pool -= left;
        return left;
    }

    /** The fees collected so far, handed to the creator once. */
    public long takeFees() {
        long taken = fees;
        fees = 0;
        return taken;
    }

    /** What a ticket of these shares pays once the market is finished, rounded down. */
    public long payout(Side side, long milli) {
        if (milli <= 0) return 0;
        long value = switch (status) {
            case RESOLVED -> side == outcome ? milli * shareValue / MILLI : 0;
            case CANCELLED -> (long) Math.floor((double) milli * shareValue * priceBasisPoints(side) / ((double) MILLI * BASIS));
            default -> throw new IllegalStateException("The market is not finished");
        };
        return Math.min(pool, value);
    }

    /** Cashes a ticket of a finished market: its shares leave and the pool pays them. */
    public long redeem(Side side, long milli) {
        if (!status.finished()) throw new IllegalStateException("The market is not finished");
        if (milli <= 0 || milli > shares(side)) throw new IllegalArgumentException("Not that many shares outstanding");
        long paid = payout(side, milli);
        if (side.yes()) yes -= milli;
        else no -= milli;
        pool -= paid;
        return paid;
    }
}
