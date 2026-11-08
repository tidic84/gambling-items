package dev.gamblingitems.fabric.slots;

import dev.gamblingitems.fabric.value.ValueCatalog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Everything the slot machine screen needs: the shared item values, this cabinet's tuning, and what
 * an arcade ticket is worth when it pays in tickets (0 when it pays in items).
 */
public record SlotSetup(ValueCatalog catalog, SlotSettings settings, long ticketValue) {
    public SlotSetup(ValueCatalog catalog, SlotSettings settings) { this(catalog, settings, 0); }

    public boolean paysTickets() { return ticketValue > 0; }

    public static final StreamCodec<RegistryFriendlyByteBuf, SlotSetup> CODEC = new StreamCodec<>() {
        @Override public SlotSetup decode(RegistryFriendlyByteBuf buffer) {
            return new SlotSetup(ValueCatalog.CODEC.decode(buffer), SlotSettings.CODEC.decode(buffer), buffer.readVarLong());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, SlotSetup data) {
            ValueCatalog.CODEC.encode(buffer, data.catalog());
            SlotSettings.CODEC.encode(buffer, data.settings());
            buffer.writeVarLong(data.ticketValue());
        }
    };
}
