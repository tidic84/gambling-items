package dev.gamblingitems.fabric.prediction;

import dev.gamblingitems.fabric.value.ValueCatalog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Everything the prediction market screen needs: the shared item values and the server's tuning. */
public record PredictionSetup(ValueCatalog catalog, PredictionSettings settings) {
    public static final StreamCodec<RegistryFriendlyByteBuf, PredictionSetup> CODEC = new StreamCodec<>() {
        @Override public PredictionSetup decode(RegistryFriendlyByteBuf buffer) {
            return new PredictionSetup(ValueCatalog.CODEC.decode(buffer), PredictionSettings.CODEC.decode(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, PredictionSetup data) {
            ValueCatalog.CODEC.encode(buffer, data.catalog());
            PredictionSettings.CODEC.encode(buffer, data.settings());
        }
    };
}
