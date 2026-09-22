package dev.gamblingitems.fabric.bingo;

import dev.gamblingitems.fabric.value.ValueCatalog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Everything the bingo screen needs: the shared item values and this table's tuning. */
public record BingoSetup(ValueCatalog catalog, BingoSettings settings) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BingoSetup> CODEC = new StreamCodec<>() {
        @Override public BingoSetup decode(RegistryFriendlyByteBuf buffer) {
            return new BingoSetup(ValueCatalog.CODEC.decode(buffer), BingoSettings.CODEC.decode(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, BingoSetup data) {
            ValueCatalog.CODEC.encode(buffer, data.catalog());
            BingoSettings.CODEC.encode(buffer, data.settings());
        }
    };
}
