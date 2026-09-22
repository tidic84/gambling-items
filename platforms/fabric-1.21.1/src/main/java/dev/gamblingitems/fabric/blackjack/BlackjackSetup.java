package dev.gamblingitems.fabric.blackjack;

import dev.gamblingitems.fabric.value.ValueCatalog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Everything the blackjack screen needs: the shared item values and this table's tuning. */
public record BlackjackSetup(ValueCatalog catalog, BlackjackSettings settings) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BlackjackSetup> CODEC = new StreamCodec<>() {
        @Override public BlackjackSetup decode(RegistryFriendlyByteBuf buffer) {
            return new BlackjackSetup(ValueCatalog.CODEC.decode(buffer), BlackjackSettings.CODEC.decode(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, BlackjackSetup data) {
            ValueCatalog.CODEC.encode(buffer, data.catalog());
            BlackjackSettings.CODEC.encode(buffer, data.settings());
        }
    };
}
