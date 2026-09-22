package dev.gamblingitems.fabric.slots;

import dev.gamblingitems.fabric.value.ValueCatalog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Everything the slot machine screen needs: the shared item values and this cabinet's tuning. */
public record SlotSetup(ValueCatalog catalog, SlotSettings settings) {
    public static final StreamCodec<RegistryFriendlyByteBuf, SlotSetup> CODEC = new StreamCodec<>() {
        @Override public SlotSetup decode(RegistryFriendlyByteBuf buffer) {
            return new SlotSetup(ValueCatalog.CODEC.decode(buffer), SlotSettings.CODEC.decode(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, SlotSetup data) {
            ValueCatalog.CODEC.encode(buffer, data.catalog());
            SlotSettings.CODEC.encode(buffer, data.settings());
        }
    };
}
