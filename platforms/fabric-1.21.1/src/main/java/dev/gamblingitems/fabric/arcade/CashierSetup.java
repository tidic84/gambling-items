package dev.gamblingitems.fabric.arcade;

import dev.gamblingitems.fabric.value.ValueCatalog;
import dev.gamblingitems.fabric.vault.ArcadeLedger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** What the cashier's window shows: the item values, the arcade's prizes and chips, and the player's last plays. */
public record CashierSetup(ValueCatalog catalog, ArcadeSettings arcade, List<ArcadeLedger.Play> history) {
    public CashierSetup {
        history = List.copyOf(history);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CashierSetup> CODEC = new StreamCodec<>() {
        @Override public CashierSetup decode(RegistryFriendlyByteBuf buffer) {
            ValueCatalog catalog = ValueCatalog.CODEC.decode(buffer);
            ArcadeSettings arcade = ArcadeSettings.CODEC.decode(buffer);
            int count = buffer.readVarInt();
            if (count < 0 || count > ArcadeLedger.HISTORY) throw new IllegalArgumentException("Invalid history length " + count);
            List<ArcadeLedger.Play> history = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                history.add(new ArcadeLedger.Play(buffer.readUtf(32), buffer.readVarLong(), buffer.readVarInt(),
                        buffer.readVarLong(), buffer.readVarLong()));
            }
            return new CashierSetup(catalog, arcade, history);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, CashierSetup data) {
            ValueCatalog.CODEC.encode(buffer, data.catalog());
            ArcadeSettings.CODEC.encode(buffer, data.arcade());
            buffer.writeVarInt(data.history().size());
            for (ArcadeLedger.Play play : data.history()) {
                buffer.writeUtf(play.mode(), 32);
                buffer.writeVarLong(play.stake());
                buffer.writeVarInt(play.multiplier());
                buffer.writeVarLong(play.tickets());
                buffer.writeVarLong(play.at());
            }
        }
    };
}
