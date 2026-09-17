package dev.gamblingitems.core.upgrade;

import java.math.BigInteger;

/** Fixed-point values; all arguments must use the same unit. */
public final class ItemValues {
    private ItemValues() {}

    public static long stackValue(long unitValue, int count, int damage, int maximumDamage) {
        if (unitValue <= 0 || count <= 0 || maximumDamage < 0 || damage < 0
                || damage > maximumDamage) {
            throw new IllegalArgumentException("Invalid value, quantity or durability");
        }
        BigInteger value = BigInteger.valueOf(unitValue).multiply(BigInteger.valueOf(count));
        if (maximumDamage > 0) {
            value = value.multiply(BigInteger.valueOf(maximumDamage - damage))
                    .divide(BigInteger.valueOf(maximumDamage));
        }
        return value.longValueExact();
    }
}
