package dev.gamblingitems.core.cases;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Pure rules for a weighted reward table. Item identity, randomness, payment and inventories
 * belong to the server integration.
 *
 * <p>An administrator writes relative weights; they are normalised once, to parts per million,
 * so a table can be sent, stored and drawn without floating point on the receiving side.
 * A rarity is a display category here, never an automatic probability.
 */
public final class CaseRules {
    public static final int TOTAL_WEIGHT = 1_000_000;
    public static final int MAX_REWARDS = 32;
    public static final int MIN_REWARDS = 2;

    private CaseRules() {}

    /**
     * Relative weights become parts per million, summing to exactly {@link #TOTAL_WEIGHT}.
     * Every announced reward keeps at least one millionth, so nothing shown is unreachable.
     */
    public static int[] normalize(List<Long> rawWeights) {
        Objects.requireNonNull(rawWeights, "rawWeights");
        if (rawWeights.size() < MIN_REWARDS || rawWeights.size() > MAX_REWARDS) {
            throw new IllegalArgumentException("A case holds between " + MIN_REWARDS + " and " + MAX_REWARDS + " rewards");
        }
        double total = 0;
        for (long weight : rawWeights) {
            if (weight <= 0 || weight > TOTAL_WEIGHT) throw new IllegalArgumentException("Invalid reward weight");
            total += weight;
        }
        double[] shares = new double[rawWeights.size()];
        int[] weights = new int[rawWeights.size()];
        int assigned = 0;
        for (int index = 0; index < shares.length; index++) {
            shares[index] = rawWeights.get(index) / total * TOTAL_WEIGHT;
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
        for (int index = 0; index < weights.length; index++) {
            if (weights[index] != 0) continue;
            int largest = 0;
            for (int other = 0; other < weights.length; other++) {
                if (weights[other] > weights[largest]) largest = other;
            }
            if (weights[largest] <= 1) throw new IllegalArgumentException("Too many rewards for this case");
            weights[largest]--;
            weights[index] = 1;
        }
        return weights;
    }

    /** The caller supplies an independent server-side draw in [0, TOTAL_WEIGHT). */
    public static int select(int[] weights, int draw) {
        Objects.requireNonNull(weights, "weights");
        if (draw < 0 || draw >= TOTAL_WEIGHT) throw new IllegalArgumentException("draw is outside the table");
        int cumulated = 0;
        for (int index = 0; index < weights.length; index++) {
            cumulated += weights[index];
            if (draw < cumulated) return index;
        }
        throw new IllegalStateException("Incomplete case table");
    }

    /** Effective probability of one reward, as a percentage, computed from the weights themselves. */
    public static BigDecimal percentOf(int weight) {
        if (weight <= 0 || weight > TOTAL_WEIGHT) throw new IllegalArgumentException("Invalid reward weight");
        return BigDecimal.valueOf(weight, 4);
    }

    /** Average value of one opening; the values are those of the whole reward stacks. */
    public static BigDecimal averageValue(int[] weights, List<Long> rewardValues) {
        if (weights.length != rewardValues.size()) throw new IllegalArgumentException("Table size mismatch");
        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < weights.length; index++) {
            long value = rewardValues.get(index);
            if (value <= 0) throw new IllegalArgumentException("Every reward needs a value");
            total = total.add(BigDecimal.valueOf(weights[index]).multiply(BigDecimal.valueOf(value)));
        }
        return total.divide(BigDecimal.valueOf(TOTAL_WEIGHT), 6, RoundingMode.HALF_UP);
    }

    /** What an opening returns on average, relative to its price. Shown, never silently corrected. */
    public static BigDecimal returnRate(int[] weights, List<Long> rewardValues, long priceValue) {
        if (priceValue <= 0) throw new IllegalArgumentException("A case needs a priced entry");
        return averageValue(weights, rewardValues)
                .divide(BigDecimal.valueOf(priceValue), 6, RoundingMode.HALF_UP);
    }
}
