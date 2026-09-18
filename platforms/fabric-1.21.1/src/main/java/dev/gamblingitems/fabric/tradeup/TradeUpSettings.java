package dev.gamblingitems.fabric.tradeup;

import dev.gamblingitems.core.tradeup.TradeUpRules;
import java.math.BigDecimal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Server tuning of the trade up contract. Basis points keep the file and the protocol integer only. */
public record TradeUpSettings(int requiredUnits, int unitRatioBasisPoints, int returnBasisPoints,
                              int rewardCapBasisPoints, int rewardCount) {
    public TradeUpSettings {
        if (requiredUnits < 2 || requiredUnits > 64
                || unitRatioBasisPoints < 10_000 || unitRatioBasisPoints > 100_000
                || returnBasisPoints < 1 || returnBasisPoints > 10_000
                || rewardCapBasisPoints <= 10_000 || rewardCapBasisPoints > 1_000_000
                || rewardCount < 2 || rewardCount > TradeUpRules.MAX_REWARDS) {
            throw new IllegalArgumentException("Invalid trade up settings");
        }
    }

    public TradeUpRules rules() {
        return new TradeUpRules(requiredUnits, BigDecimal.valueOf(unitRatioBasisPoints, 4),
                BigDecimal.valueOf(returnBasisPoints, 4), BigDecimal.valueOf(rewardCapBasisPoints, 4));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeUpSettings> CODEC = new StreamCodec<>() {
        @Override public TradeUpSettings decode(RegistryFriendlyByteBuf buffer) {
            return new TradeUpSettings(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TradeUpSettings data) {
            buffer.writeVarInt(data.requiredUnits());
            buffer.writeVarInt(data.unitRatioBasisPoints());
            buffer.writeVarInt(data.returnBasisPoints());
            buffer.writeVarInt(data.rewardCapBasisPoints());
            buffer.writeVarInt(data.rewardCount());
        }
    };
}
