package dev.gamblingitems.fabric.crash;

import dev.gamblingitems.core.crash.CrashRules;
import java.math.BigDecimal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server tuning of a crash table. A round keeps the settings it started with, so a reload never
 * changes a flight in progress.
 *
 * <p>A bet is made of any priced items, not of one imposed material: what counts is their value.
 * There is no configured maximum bet either; a bet is refused only when the win it could produce
 * would not fit in the winnings of that player.
 */
public record CrashSettings(long minimumStake, int returnBasisPoints, int maximumMultiplier,
                            int growthBasisPoints, int bettingTicks, int resultTicks) {
    /** Slots of the crash vault: the bet being prepared, the engaged bet, then the winnings. */
    public static final int STAKE_SLOTS = 5;
    public static final int INPUT_SLOT = 0;
    public static final int ENGAGED_SLOT = INPUT_SLOT + STAKE_SLOTS;
    public static final int FIRST_PAYOUT_SLOT = ENGAGED_SLOT + STAKE_SLOTS;
    public static final int PAYOUT_SLOTS = 54;
    public static final int VAULT_SIZE = FIRST_PAYOUT_SLOT + PAYOUT_SLOTS;

    public CrashSettings {
        if (minimumStake < 1 || minimumStake > 1_000_000_000L
                || returnBasisPoints < 1 || returnBasisPoints > 10_000
                || maximumMultiplier <= CrashRules.START || maximumMultiplier > CrashRules.MAX_MULTIPLIER
                || growthBasisPoints <= 10_000 || growthBasisPoints > 20_000
                || bettingTicks < 40 || bettingTicks > 1_200
                || resultTicks < 20 || resultTicks > 400) {
            throw new IllegalArgumentException("Invalid crash settings");
        }
    }

    public CrashRules rules() {
        return new CrashRules(BigDecimal.valueOf(returnBasisPoints, 4), maximumMultiplier,
                BigDecimal.valueOf(growthBasisPoints, 4));
    }

    /** The value a bet could reach at the maximum multiplier, which a player must be able to hold. */
    public long maximumPayout(long stakeValue) { return rules().payout(stakeValue, maximumMultiplier); }

    public static final StreamCodec<RegistryFriendlyByteBuf, CrashSettings> CODEC = new StreamCodec<>() {
        @Override public CrashSettings decode(RegistryFriendlyByteBuf buffer) {
            return new CrashSettings(buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, CrashSettings data) {
            buffer.writeVarLong(data.minimumStake());
            buffer.writeVarInt(data.returnBasisPoints());
            buffer.writeVarInt(data.maximumMultiplier());
            buffer.writeVarInt(data.growthBasisPoints());
            buffer.writeVarInt(data.bettingTicks());
            buffer.writeVarInt(data.resultTicks());
        }
    };
}
