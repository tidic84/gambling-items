package dev.gamblingitems.fabric.roulette;

import dev.gamblingitems.fabric.value.ValueCatalog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Everything the roulette screen needs: the shared item values and this table's tuning. */
public record RouletteSetup(ValueCatalog catalog, RouletteSettings settings) {
    public static final StreamCodec<RegistryFriendlyByteBuf, RouletteSetup> CODEC = new StreamCodec<>() {
        @Override public RouletteSetup decode(RegistryFriendlyByteBuf buffer) {
            return new RouletteSetup(ValueCatalog.CODEC.decode(buffer), RouletteSettings.CODEC.decode(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, RouletteSetup data) {
            ValueCatalog.CODEC.encode(buffer, data.catalog());
            RouletteSettings.CODEC.encode(buffer, data.settings());
        }
    };
}
