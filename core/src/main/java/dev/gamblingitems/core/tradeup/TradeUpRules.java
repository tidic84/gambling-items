package dev.gamblingitems.core.tradeup;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Pure rules for the trade up contract: several comparable items become one better item.
 * Item identity, randomness, payment and inventories belong to the server integration.
 *
 * <p>Weights use parts per million so a table can be sent, stored and drawn without
 * floating point on the receiving side.
 */
public record TradeUpRules(int requiredUnits, BigDecimal maximumUnitRatio, BigDecimal returnRate,
                           BigDecimal maximumRewardRatio) {
    public static final int TOTAL_WEIGHT = 1_000_000;
    public static final int MAX_REWARDS = 32;

    public TradeUpRules {
        Objects.requireNonNull(maximumUnitRatio, "maximumUnitRatio");
        Objects.requireNonNull(returnRate, "returnRate");
        Objects.requireNonNull(maximumRewardRatio, "maximumRewardRatio");
        if (requiredUnits < 2 || requiredUnits > 64
                || maximumUnitRatio.compareTo(BigDecimal.ONE) < 0
                || returnRate.signum() <= 0 || returnRate.compareTo(BigDecimal.ONE) > 0
                || maximumRewardRatio.compareTo(BigDecimal.ONE) <= 0) {
            throw new IllegalArgumentException("Invalid trade up settings");
        }
    }

    /** One entry per consumed item, never per occupied slot. Damaged tools use their effective value. */
    public boolean acceptsStake(List<Long> unitValues) {
        Objects.requireNonNull(unitValues, "unitValues");
        if (unitValues.size() != requiredUnits) return false;
        long lowest = Long.MAX_VALUE;
        long highest = 0;
        for (long value : unitValues) {
            if (value <= 0) return false;
            lowest = Math.min(lowest, value);
            highest = Math.max(highest, value);
        }
        return BigDecimal.valueOf(highest)
                .compareTo(maximumUnitRatio.multiply(BigDecimal.valueOf(lowest))) <= 0;
    }

    /** A reward beats every single input on its own; it does not have to beat their sum. */
    public boolean isEligibleReward(long rewardValue, long bestUnitValue, long totalStake) {
        if (rewardValue <= 0 || bestUnitValue <= 0 || totalStake <= 0) return false;
        return rewardValue > bestUnitValue
                && BigDecimal.valueOf(rewardValue)
                        .compareTo(maximumRewardRatio.multiply(BigDecimal.valueOf(totalStake))) <= 0;
    }

    public BigDecimal targetValue(long totalStake) {
        if (totalStake <= 0) throw new IllegalArgumentException("A trade up needs a positive stake");
        return returnRate.multiply(BigDecimal.valueOf(totalStake));
    }

    /**
     * True when a table of these rewards can average the configured return.
     * The rewards must be sorted by ascending value, with one below and one above the target.
     */
    public boolean canBuildTable(long totalStake, List<Long> rewardValues) {
        if (rewardValues == null || rewardValues.size() < 2 || rewardValues.size() > MAX_REWARDS) return false;
        BigDecimal target = targetValue(totalStake);
        return BigDecimal.valueOf(rewardValues.get(0)).compareTo(target) < 0
                && BigDecimal.valueOf(rewardValues.get(rewardValues.size() - 1)).compareTo(target) > 0;
    }

    /**
     * Weights in parts per million for rewards sorted by ascending value.
     * Cheap rewards are favoured exactly as much as the configured return demands.
     */
    public int[] weights(long totalStake, List<Long> rewardValues) {
        if (!canBuildTable(totalStake, rewardValues)) {
            throw new IllegalArgumentException("These rewards cannot average the configured return");
        }
        long[] values = new long[rewardValues.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = rewardValues.get(index);
            if (values[index] <= 0 || (index > 0 && values[index] < values[index - 1])) {
                throw new IllegalArgumentException("Rewards must be positive and sorted by ascending value");
            }
        }
        double target = targetValue(totalStake).doubleValue();
        double low = -256;
        double high = 256;
        for (int step = 0; step < 200; step++) {
            double middle = (low + high) / 2;
            if (average(values, middle) > target) low = middle;
            else high = middle;
        }
        return distribute(values, (low + high) / 2);
    }

    /** The caller supplies an independent server-side draw in [0, TOTAL_WEIGHT). */
    public int select(int[] weights, int draw) {
        Objects.requireNonNull(weights, "weights");
        if (draw < 0 || draw >= TOTAL_WEIGHT) throw new IllegalArgumentException("draw is outside the table");
        int cumulated = 0;
        for (int index = 0; index < weights.length; index++) {
            cumulated += weights[index];
            if (draw < cumulated) return index;
        }
        throw new IllegalStateException("Incomplete trade up table");
    }

    public BigDecimal averageValue(int[] weights, List<Long> rewardValues) {
        if (weights.length != rewardValues.size()) throw new IllegalArgumentException("Table size mismatch");
        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < weights.length; index++) {
            total = total.add(BigDecimal.valueOf(weights[index])
                    .multiply(BigDecimal.valueOf(rewardValues.get(index))));
        }
        return total.divide(BigDecimal.valueOf(TOTAL_WEIGHT), 6, RoundingMode.HALF_UP);
    }

    /** Softmax over a tilt applied to log(value), evaluated in log space to avoid overflow. */
    private static double average(long[] values, double tilt) {
        double[] shares = shares(values, tilt);
        double sum = 0;
        double weighted = 0;
        for (int index = 0; index < values.length; index++) {
            sum += shares[index];
            weighted += shares[index] * values[index];
        }
        return weighted / sum;
    }

    private static double[] shares(long[] values, double tilt) {
        double[] shares = new double[values.length];
        double highest = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < values.length; index++) {
            shares[index] = tilt * StrictMath.log((double) values[0] / values[index]);
            highest = Math.max(highest, shares[index]);
        }
        for (int index = 0; index < values.length; index++) {
            shares[index] = StrictMath.exp(shares[index] - highest);
        }
        return shares;
    }

    private static int[] distribute(long[] values, double tilt) {
        double[] shares = shares(values, tilt);
        double sum = 0;
        for (double share : shares) sum += share;
        int[] weights = new int[values.length];
        int assigned = 0;
        for (int index = 0; index < values.length; index++) {
            shares[index] = shares[index] / sum * TOTAL_WEIGHT;
            weights[index] = (int) Math.floor(shares[index]);
            assigned += weights[index];
        }
        // Largest remainder first, so every machine builds the same table.
        while (assigned < TOTAL_WEIGHT) {
            int best = 0;
            double bestRemainder = -1;
            for (int index = 0; index < weights.length; index++) {
                double remainder = shares[index] - weights[index];
                if (remainder > bestRemainder) {
                    bestRemainder = remainder;
                    best = index;
                }
            }
            weights[best]++;
            assigned++;
        }
        // An announced reward stays reachable. Taking one millionth from the largest entry
        // changes the average far less than the displayed precision.
        for (int index = 0; index < weights.length; index++) {
            if (weights[index] != 0) continue;
            int largest = 0;
            for (int other = 0; other < weights.length; other++) {
                if (weights[other] > weights[largest]) largest = other;
            }
            if (weights[largest] <= 1) throw new IllegalArgumentException("Too many rewards for this table");
            weights[largest]--;
            weights[index] = 1;
        }
        return weights;
    }
}
