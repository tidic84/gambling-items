package dev.gamblingitems.fabric.cases;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** Every case the server offers, in configuration order. Sent once when a case menu opens. */
public record CaseCatalog(List<CaseDefinition> cases) {
    public static final int MAX_CASES = 16;

    public CaseCatalog {
        cases = List.copyOf(cases);
        if (cases.isEmpty() || cases.size() > MAX_CASES
                || cases.stream().map(CaseDefinition::id).distinct().count() != cases.size()) {
            throw new IllegalArgumentException("Invalid case catalogue");
        }
    }

    public CaseDefinition get(int index) {
        return index < 0 || index >= cases.size() ? null : cases.get(index);
    }

    /** True when the item pays for one of the offered cases, whatever the current selection. */
    public boolean isPrice(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (CaseDefinition definition : cases) {
            ItemStack single = new ItemStack(BuiltInRegistries.ITEM.get(definition.priceItem()));
            if (ItemStack.isSameItemSameComponents(stack, single)) return true;
        }
        return false;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CaseCatalog> CODEC = new StreamCodec<>() {
        @Override public CaseCatalog decode(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 1 || count > MAX_CASES) throw new IllegalArgumentException("Invalid case catalogue length");
            List<CaseDefinition> cases = new ArrayList<>(count);
            for (int index = 0; index < count; index++) cases.add(CaseDefinition.CODEC.decode(buffer));
            return new CaseCatalog(cases);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, CaseCatalog data) {
            buffer.writeVarInt(data.cases().size());
            for (CaseDefinition definition : data.cases()) CaseDefinition.CODEC.encode(buffer, definition);
        }
    };
}
