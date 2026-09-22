package dev.gamblingitems.fabric.bingo;

import java.math.BigDecimal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server tuning of a bingo table: what a card costs, how fast the drum turns, and what share of
 * the pot goes back to the players. The card itself is the one everybody knows and is not tuned.
 */
public record BingoSettings(long cardPrice, int returnBasisPoints, int bettingTicks,
                            int drawTicks, int resultTicks) {
    /** Slots of the bingo vault: the price being prepared, the price paid in, then the winnings. */
    public static final int STAKE_SLOTS = 5;
    public static final int INPUT_SLOT = 0;
    public static final int ENGAGED_SLOT = INPUT_SLOT + STAKE_SLOTS;
    public static final int FIRST_PAYOUT_SLOT = ENGAGED_SLOT + STAKE_SLOTS;
    public static final int PAYOUT_SLOTS = 54;
    public static final int VAULT_SIZE = FIRST_PAYOUT_SLOT + PAYOUT_SLOTS;

    public BingoSettings {
        if (cardPrice < 1 || cardPrice > 1_000_000_000L
                || returnBasisPoints < 1 || returnBasisPoints > 10_000
                || bettingTicks < 40 || bettingTicks > 2_400
                || drawTicks < 5 || drawTicks > 200
                || resultTicks < 20 || resultTicks > 400) {
            throw new IllegalArgumentException("Invalid bingo settings");
        }
    }

    public BigDecimal returnRate() { return BigDecimal.valueOf(returnBasisPoints, 4); }

    public static final StreamCodec<RegistryFriendlyByteBuf, BingoSettings> CODEC = new StreamCodec<>() {
        @Override public BingoSettings decode(RegistryFriendlyByteBuf buffer) {
            return new BingoSettings(buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, BingoSettings data) {
            buffer.writeVarLong(data.cardPrice());
            buffer.writeVarInt(data.returnBasisPoints());
            buffer.writeVarInt(data.bettingTicks());
            buffer.writeVarInt(data.drawTicks());
            buffer.writeVarInt(data.resultTicks());
        }
    };
}
