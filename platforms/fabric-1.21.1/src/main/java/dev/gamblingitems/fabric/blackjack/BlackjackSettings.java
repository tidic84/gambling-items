package dev.gamblingitems.fabric.blackjack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server tuning of a blackjack table. The rules themselves are the ones printed on a felt:
 * the dealer stands on every seventeen and a natural pays three to two, so they are not configured.
 *
 * <p>A hand is staked with any priced items; what counts is their value.
 */
public record BlackjackSettings(long minimumStake, int dealerDelayTicks, int resultTicks) {
    /** Slots of the blackjack vault: the chips being prepared, the engaged bet, then the winnings. */
    public static final int STAKE_SLOTS = 5;
    public static final int INPUT_SLOT = 0;
    public static final int ENGAGED_SLOT = INPUT_SLOT + STAKE_SLOTS;
    public static final int FIRST_PAYOUT_SLOT = ENGAGED_SLOT + STAKE_SLOTS;
    public static final int PAYOUT_SLOTS = 54;
    public static final int VAULT_SIZE = FIRST_PAYOUT_SLOT + PAYOUT_SLOTS;
    /** The most cards a hand can hold before it must bust, which bounds what is synchronised. */
    public static final int MAX_CARDS = 11;

    public BlackjackSettings {
        if (minimumStake < 1 || minimumStake > 1_000_000_000L
                || dealerDelayTicks < 1 || dealerDelayTicks > 100
                || resultTicks < 20 || resultTicks > 400) {
            throw new IllegalArgumentException("Invalid blackjack settings");
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, BlackjackSettings> CODEC = new StreamCodec<>() {
        @Override public BlackjackSettings decode(RegistryFriendlyByteBuf buffer) {
            return new BlackjackSettings(buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, BlackjackSettings data) {
            buffer.writeVarLong(data.minimumStake());
            buffer.writeVarInt(data.dealerDelayTicks());
            buffer.writeVarInt(data.resultTicks());
        }
    };
}
