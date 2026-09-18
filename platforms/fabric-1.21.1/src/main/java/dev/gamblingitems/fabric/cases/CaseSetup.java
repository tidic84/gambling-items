package dev.gamblingitems.fabric.cases;

import dev.gamblingitems.fabric.value.ValueCatalog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Everything the case screen needs: the shared item values and the offered cases. */
public record CaseSetup(ValueCatalog catalog, CaseCatalog cases) {
    public static final StreamCodec<RegistryFriendlyByteBuf, CaseSetup> CODEC = new StreamCodec<>() {
        @Override public CaseSetup decode(RegistryFriendlyByteBuf buffer) {
            return new CaseSetup(ValueCatalog.CODEC.decode(buffer), CaseCatalog.CODEC.decode(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, CaseSetup data) {
            ValueCatalog.CODEC.encode(buffer, data.catalog());
            CaseCatalog.CODEC.encode(buffer, data.cases());
        }
    };
}
