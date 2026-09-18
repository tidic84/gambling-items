package dev.gamblingitems.fabric.crash;

import dev.gamblingitems.fabric.value.ValueCatalog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Everything the crash screen needs: the shared item values and this table's tuning. */
public record CrashSetup(ValueCatalog catalog, CrashSettings settings) {
    public static final StreamCodec<RegistryFriendlyByteBuf, CrashSetup> CODEC = new StreamCodec<>() {
        @Override public CrashSetup decode(RegistryFriendlyByteBuf buffer) {
            return new CrashSetup(ValueCatalog.CODEC.decode(buffer), CrashSettings.CODEC.decode(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, CrashSetup data) {
            ValueCatalog.CODEC.encode(buffer, data.catalog());
            CrashSettings.CODEC.encode(buffer, data.settings());
        }
    };
}
