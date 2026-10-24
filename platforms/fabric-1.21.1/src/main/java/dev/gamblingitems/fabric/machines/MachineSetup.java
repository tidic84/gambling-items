package dev.gamblingitems.fabric.machines;

import dev.gamblingitems.fabric.value.ValueCatalog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Everything a machine window needs: the item values it is played with and that machine's tuning. */
public record MachineSetup(ValueCatalog catalog, MachineSettings settings) {
    public static final StreamCodec<RegistryFriendlyByteBuf, MachineSetup> CODEC = new StreamCodec<>() {
        @Override public MachineSetup decode(RegistryFriendlyByteBuf buffer) {
            return new MachineSetup(ValueCatalog.CODEC.decode(buffer), MachineSettings.CODEC.decode(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, MachineSetup data) {
            ValueCatalog.CODEC.encode(buffer, data.catalog());
            MachineSettings.CODEC.encode(buffer, data.settings());
        }
    };
}
