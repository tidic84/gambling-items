package dev.gamblingitems.fabric.slots;

import dev.gamblingitems.core.slots.SlotRules;
import java.math.BigDecimal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server tuning of a slot machine. The paytable itself is printed in {@link SlotRules}: only the
 * smallest bet, the share given back and the pace of the reels belong to the server.
 *
 * <p>A bet is any priced item, in whatever quantity: what counts is the value prepared in front
 * of the machine. There is no configured maximum, only what the player could be paid.
 */
public record SlotSettings(long minimumStake, int returnBasisPoints, int spinTicks, int resultTicks) {
    /** Slots of the cabinet vault: the credit being prepared, then the winnings. */
    public static final int STAKE_SLOTS = 5;
    public static final int INPUT_SLOT = 0;
    public static final int FIRST_PAYOUT_SLOT = INPUT_SLOT + STAKE_SLOTS;
    public static final int PAYOUT_SLOTS = 54;
    public static final int VAULT_SIZE = FIRST_PAYOUT_SLOT + PAYOUT_SLOTS;

    public SlotSettings {
        if (minimumStake < 1 || minimumStake > 1_000_000_000L
                || returnBasisPoints < 1 || returnBasisPoints > 10_000
                || spinTicks < 10 || spinTicks > 200
                || resultTicks < 10 || resultTicks > 400) {
            throw new IllegalArgumentException("Invalid slot machine settings");
        }
    }

    public BigDecimal returnRate() { return BigDecimal.valueOf(returnBasisPoints, 4); }

    /** The most this machine could ever owe for that bet, which the player must be able to hold. */
    public long maximumPayout(long stake) {
        return SlotRules.payout(stake, SlotRules.bestMultiplier(), returnRate());
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SlotSettings> CODEC = new StreamCodec<>() {
        @Override public SlotSettings decode(RegistryFriendlyByteBuf buffer) {
            return new SlotSettings(buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, SlotSettings data) {
            buffer.writeVarLong(data.minimumStake());
            buffer.writeVarInt(data.returnBasisPoints());
            buffer.writeVarInt(data.spinTicks());
            buffer.writeVarInt(data.resultTicks());
        }
    };
}
