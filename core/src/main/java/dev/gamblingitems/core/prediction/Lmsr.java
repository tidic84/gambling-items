package dev.gamblingitems.core.prediction;

/**
 * The automated market maker of a yes or no question: Hanson's logarithmic market scoring rule.
 *
 * <p>Quantities are counted in shares, one share paying one unit if its side comes out. The maker
 * always quotes a price, so a question can be traded by a single player as well as by a crowd, and
 * the price of YES reads as the probability the traders give to the event. Whatever happens, the
 * maker never loses more than {@link #worstLoss()}, which is what the creator of a market deposits.
 *
 * <p>Every formula is written in its stable log-sum-exp form, so large positions never overflow.
 */
public final class Lmsr {
    private static final double LN2 = Math.log(2);
    private final double liquidity;

    public Lmsr(double liquidity) {
        if (!(liquidity > 0) || Double.isInfinite(liquidity)) throw new IllegalArgumentException("Invalid liquidity");
        this.liquidity = liquidity;
    }

    /** The liquidity whose worst loss is exactly this subsidy, in shares. */
    public static Lmsr funded(double subsidy) { return new Lmsr(subsidy / LN2); }

    public double liquidity() { return liquidity; }

    /** The most the maker can lose on this question, in shares. */
    public double worstLoss() { return liquidity * LN2; }

    /** The cost function: what has been paid in so far for these outstanding shares. */
    public double cost(double yes, double no) {
        double top = Math.max(yes, no);
        return top + liquidity * Math.log(Math.exp((yes - top) / liquidity) + Math.exp((no - top) / liquidity));
    }

    /** The price of one YES share, between zero and one. NO costs the rest. */
    public double priceYes(double yes, double no) {
        return 1 / (1 + Math.exp((no - yes) / liquidity));
    }

    public double price(boolean onYes, double yes, double no) {
        double priceYes = priceYes(yes, no);
        return onYes ? priceYes : 1 - priceYes;
    }

    /** How many shares of a side this spend buys, everything paid going to the maker. */
    public double sharesFor(boolean onYes, double yes, double no, double spend) {
        if (spend < 0) throw new IllegalArgumentException("Negative spend");
        if (spend == 0) return 0;
        double own = onYes ? yes : no, other = onYes ? no : yes;
        double target = cost(yes, no) + spend;
        // Solves cost(own', other) = target for own'.
        double after = target + liquidity * Math.log1p(-Math.exp((other - target) / liquidity));
        return Math.max(0, after - own);
    }

    /** What buying these shares of a side costs. */
    public double buyCost(boolean onYes, double yes, double no, double shares) {
        if (shares < 0) throw new IllegalArgumentException("Negative shares");
        return onYes ? cost(yes + shares, no) - cost(yes, no) : cost(yes, no + shares) - cost(yes, no);
    }

    /** What selling these shares of a side back to the maker returns. */
    public double sellValue(boolean onYes, double yes, double no, double shares) {
        if (shares < 0) throw new IllegalArgumentException("Negative shares");
        if (onYes && shares > yes + 1e-9 || !onYes && shares > no + 1e-9) {
            throw new IllegalArgumentException("Selling more shares than are outstanding");
        }
        return onYes ? cost(yes, no) - cost(Math.max(0, yes - shares), no)
                : cost(yes, no) - cost(yes, Math.max(0, no - shares));
    }
}
