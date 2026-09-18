package dev.gamblingitems.fabric.upgrade;

import dev.gamblingitems.core.upgrade.UpgradeRules;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.math.BigDecimal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Everything the upgrader screen needs: the shared item values and this game's tuning. */
public record UpgradeSetup(ValueCatalog catalog, int returnBasisPoints, int capBasisPoints) {
    public UpgradeSetup {
        if (returnBasisPoints < 1 || returnBasisPoints > 10_000
                || capBasisPoints < 1 || capBasisPoints > 10_000) {
            throw new IllegalArgumentException("Invalid upgrader settings");
        }
    }

    public UpgradeRules rules() {
        return new UpgradeRules(BigDecimal.valueOf(returnBasisPoints, 4), BigDecimal.valueOf(capBasisPoints, 4));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeSetup> CODEC = new StreamCodec<>() {
        @Override public UpgradeSetup decode(RegistryFriendlyByteBuf buffer) {
            int rate = buffer.readVarInt();
            int cap = buffer.readVarInt();
            return new UpgradeSetup(ValueCatalog.CODEC.decode(buffer), rate, cap);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, UpgradeSetup data) {
            buffer.writeVarInt(data.returnBasisPoints());
            buffer.writeVarInt(data.capBasisPoints());
            ValueCatalog.CODEC.encode(buffer, data.catalog());
        }
    };
}
