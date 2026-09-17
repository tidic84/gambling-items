package dev.gamblingitems.core.upgrade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Pure rules only. Inventory ownership, randomness and payment belong to the server integration. */
public record UpgradeRules(BigDecimal returnRate, BigDecimal maximumChance) {
    public UpgradeRules {
        requireProbability(returnRate, "returnRate");
        requireProbability(maximumChance, "maximumChance");
    }

    /** Both values use the same fixed-point unit. Reject invalid requests rather than clamp them. */
    public BigDecimal chance(long inputValue, long rewardValue) {
        if (inputValue <= 0 || rewardValue <= inputValue) {
            throw new IllegalArgumentException("An upgrade needs a positive stake and a more valuable reward");
        }
        return returnRate.multiply(BigDecimal.valueOf(inputValue))
                .divide(BigDecimal.valueOf(rewardValue), 18, RoundingMode.DOWN)
                .min(maximumChance);
    }

    /** The caller supplies an independent server-side draw in [0, 1). */
    public boolean wins(long inputValue, long rewardValue, BigDecimal draw) {
        Objects.requireNonNull(draw, "draw");
        if (draw.signum() < 0 || draw.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("draw must be in [0, 1)");
        }
        return draw.compareTo(chance(inputValue, rewardValue)) < 0;
    }

    private static void requireProbability(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + " must be in (0, 1]");
        }
    }
}
