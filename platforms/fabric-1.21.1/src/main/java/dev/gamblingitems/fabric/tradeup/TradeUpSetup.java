package dev.gamblingitems.fabric.tradeup;

import dev.gamblingitems.fabric.value.ValueCatalog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Everything the trade up screen needs: the shared item values and this game's tuning. */
public record TradeUpSetup(ValueCatalog catalog, TradeUpSettings settings) {
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeUpSetup> CODEC = new StreamCodec<>() {
        @Override public TradeUpSetup decode(RegistryFriendlyByteBuf buffer) {
            return new TradeUpSetup(ValueCatalog.CODEC.decode(buffer), TradeUpSettings.CODEC.decode(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TradeUpSetup data) {
            ValueCatalog.CODEC.encode(buffer, data.catalog());
            TradeUpSettings.CODEC.encode(buffer, data.settings());
        }
    };
}
