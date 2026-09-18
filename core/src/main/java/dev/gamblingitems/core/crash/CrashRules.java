package dev.gamblingitems.core.crash;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Pure rules of the crash round: where the flight ends, how the multiplier grows, what a cash out pays.
 * Ticks, secrecy, payments and inventories belong to the server integration.
 *
 * <p>Multipliers are hundredths, so 100 is 1.00x. Nothing here uses floating point on a result,
 * and a whole round can be replayed from its draw alone.
 *
 * <p>The crash point follows P(crash at or above m) = returnRate / m. Every cash out threshold
 * therefore returns the same configured share, which a uniform draw between two multipliers
 * would not do. The maximum multiplier truncates that tail, and lowers the return above it.
 */
public record CrashRules(BigDecimal returnRate, int maximumMultiplier, BigDecimal growthPerTick) {
    public static final int START = 100;
    public static final int MAX_MULTIPLIER = 1_000_000;

    public CrashRules {
        Objects.requireNonNull(returnRate, "returnRate");
        Objects.requireNonNull(growthPerTick, "growthPerTick");
        if (returnRate.signum() <= 0 || returnRate.compareTo(BigDecimal.ONE) > 0
                || maximumMultiplier <= START || maximumMultiplier > MAX_MULTIPLIER
                || growthPerTick.compareTo(BigDecimal.ONE) <= 0
                || growthPerTick.compareTo(BigDecimal.TWO) > 0) {
            throw new IllegalArgumentException("Invalid crash settings");
        }
    }

    /**
     * The secret end of one flight, drawn once by the server.
     * The draw is uniform in [0, bound); a draw above the return rate ends the flight at once.
     */
    public int crashPoint(long draw, long bound) {
        if (bound < 2 || draw < 0 || draw >= bound) throw new IllegalArgumentException("draw is outside the round");
        BigDecimal point = returnRate.multiply(BigDecimal.valueOf(START))
                .multiply(BigDecimal.valueOf(bound))
                .divide(BigDecimal.valueOf(draw + 1), 0, RoundingMode.FLOOR);
        if (point.compareTo(BigDecimal.valueOf(maximumMultiplier)) >= 0) return maximumMultiplier;
        return Math.max(START, point.intValueExact());
    }

    /** The public multiplier at a tick of the flight, identical on every machine. */
    public int multiplierAt(int tick) {
        if (tick < 0) throw new IllegalArgumentException("A flight starts at tick zero");
        double grown = START * StrictMath.pow(growthPerTick.doubleValue(), tick);
        if (grown >= maximumMultiplier) return maximumMultiplier;
        return Math.max(START, (int) Math.floor(grown));
    }

    /** First tick whose multiplier reaches this point; the flight ends there and pays nobody else. */
    public int crashTick(int crashPoint) {
        if (crashPoint < START || crashPoint > maximumMultiplier) {
            throw new IllegalArgumentException("Crash point outside the configured range");
        }
        int tick = 0;
        while (multiplierAt(tick) < crashPoint) tick++;
        return tick;
    }

    /** Everything the player receives, stake included, rounded down as announced. */
    public long payout(long stake, int multiplier) {
        if (stake <= 0 || multiplier < START || multiplier > maximumMultiplier) {
            throw new IllegalArgumentException("Invalid crash settlement");
        }
        return BigDecimal.valueOf(stake).multiply(BigDecimal.valueOf(multiplier))
                .divide(BigDecimal.valueOf(START), 0, RoundingMode.FLOOR).longValueExact();
    }

    /** Largest payout this configuration can ever owe, so a server can check it is storable. */
    public long maximumPayout(long maximumStake) { return payout(maximumStake, maximumMultiplier); }
}
