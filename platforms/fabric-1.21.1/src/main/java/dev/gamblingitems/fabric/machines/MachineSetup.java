package dev.gamblingitems.fabric.machines;

import dev.gamblingitems.fabric.value.ValueCatalog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Everything a machine needs: the item values it is played with, that machine's tuning, and what an
 * arcade ticket is worth when it pays in tickets (0 when it pays in items).
 */
public record MachineSetup(ValueCatalog catalog, MachineSettings settings, long ticketValue) {
    public MachineSetup(ValueCatalog catalog, MachineSettings settings) { this(catalog, settings, 0); }

    public boolean paysTickets() { return ticketValue > 0; }

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineSetup> CODEC = new StreamCodec<>() {
        @Override public MachineSetup decode(RegistryFriendlyByteBuf buffer) {
            return new MachineSetup(ValueCatalog.CODEC.decode(buffer), MachineSettings.CODEC.decode(buffer), buffer.readVarLong());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, MachineSetup data) {
            ValueCatalog.CODEC.encode(buffer, data.catalog());
            MachineSettings.CODEC.encode(buffer, data.settings());
            buffer.writeVarLong(data.ticketValue());
        }
    };
}
