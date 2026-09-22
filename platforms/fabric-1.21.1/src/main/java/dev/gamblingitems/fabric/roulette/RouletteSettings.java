package dev.gamblingitems.fabric.roulette;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server tuning of a roulette table. The wheel itself is the real one, 0 to 36, so the payouts of
 * each bet are the ones printed on the felt and are not configurable.
 *
 * <p>A bet is made of any priced items, in any quantity: what counts is their value.
 */
public record RouletteSettings(long minimumStake, int bettingTicks, int spinTicks, int resultTicks) {
    /** Slots of the roulette vault: the chips being prepared, the chips on the table, the winnings. */
    public static final int STAKE_SLOTS = 5;
    public static final int INPUT_SLOT = 0;
    public static final int ENGAGED_SLOT = INPUT_SLOT + STAKE_SLOTS;
    public static final int FIRST_PAYOUT_SLOT = ENGAGED_SLOT + STAKE_SLOTS;
    public static final int PAYOUT_SLOTS = 54;
    public static final int VAULT_SIZE = FIRST_PAYOUT_SLOT + PAYOUT_SLOTS;

    public RouletteSettings {
        if (minimumStake < 1 || minimumStake > 1_000_000_000L
                || bettingTicks < 40 || bettingTicks > 1_200
                || spinTicks < 20 || spinTicks > 400
                || resultTicks < 20 || resultTicks > 400) {
            throw new IllegalArgumentException("Invalid roulette settings");
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RouletteSettings> CODEC = new StreamCodec<>() {
        @Override public RouletteSettings decode(RegistryFriendlyByteBuf buffer) {
            return new RouletteSettings(buffer.readVarLong(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, RouletteSettings data) {
            buffer.writeVarLong(data.minimumStake());
            buffer.writeVarInt(data.bettingTicks());
            buffer.writeVarInt(data.spinTicks());
            buffer.writeVarInt(data.resultTicks());
        }
    };
}
