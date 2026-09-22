package dev.gamblingitems.fabric.battle;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server tuning of a case battle lobby: how long a lobby waits, and how fast the rounds play.
 * Everything else is the case catalogue, which the battle draws from like a case opening does.
 */
public record BattleSettings(int lobbyTicks, int roundTicks, int resultTicks) {
    /** Slots of the battle vault: the keys being prepared, the keys paid in, then the prizes. */
    public static final int STAKE_SLOTS = 4;
    public static final int INPUT_SLOT = 0;
    public static final int ENGAGED_SLOT = INPUT_SLOT + STAKE_SLOTS;
    public static final int FIRST_PAYOUT_SLOT = ENGAGED_SLOT + STAKE_SLOTS;
    public static final int PAYOUT_SLOTS = 54;
    public static final int VAULT_SIZE = FIRST_PAYOUT_SLOT + PAYOUT_SLOTS;

    public BattleSettings {
        if (lobbyTicks < 100 || lobbyTicks > 6_000
                || roundTicks < 10 || roundTicks > 200
                || resultTicks < 20 || resultTicks > 400) {
            throw new IllegalArgumentException("Invalid battle settings");
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, BattleSettings> CODEC = new StreamCodec<>() {
        @Override public BattleSettings decode(RegistryFriendlyByteBuf buffer) {
            return new BattleSettings(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, BattleSettings data) {
            buffer.writeVarInt(data.lobbyTicks());
            buffer.writeVarInt(data.roundTicks());
            buffer.writeVarInt(data.resultTicks());
        }
    };
}
