package dev.gamblingitems.fabric.upgrade;

import dev.gamblingitems.core.upgrade.ItemValues;
import dev.gamblingitems.core.upgrade.UpgradeRules;
import java.math.BigDecimal;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Immutable server snapshot, also sent when opening the menu. */
public record UpgradeCatalog(List<Entry> entries, int returnBasisPoints, int capBasisPoints) {
    public static final int MAX_ENTRIES = 512;
    public static final long MAX_VALUE = 1_000_000_000L;
    public record Entry(ResourceLocation id, long value) {
        public Entry {
            if (value <= 0 || value > MAX_VALUE) throw new IllegalArgumentException("Invalid item value");
        }
        public ItemStack stack() { return new ItemStack(BuiltInRegistries.ITEM.get(id)); }
    }

    public UpgradeCatalog {
        entries = List.copyOf(entries);
        if (entries.isEmpty() || entries.size() > MAX_ENTRIES
                || entries.stream().map(Entry::id).distinct().count() != entries.size()
                || returnBasisPoints < 1 || returnBasisPoints > 10_000
                || capBasisPoints < 1 || capBasisPoints > 10_000) {
            throw new IllegalArgumentException("Invalid upgrader catalogue");
        }
    }

    public UpgradeRules rules() {
        return new UpgradeRules(BigDecimal.valueOf(returnBasisPoints, 4), BigDecimal.valueOf(capBasisPoints, 4));
    }

    public long valueOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        ItemStack normalized = stack.copy();
        if (normalized.isDamageableItem()) normalized.setDamageValue(0);
        if (!ItemStack.isSameItemSameComponents(normalized, new ItemStack(stack.getItem()))) return 0;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (Entry entry : entries) {
            if (entry.id().equals(id)) {
                return ItemValues.stackValue(entry.value(), stack.getCount(),
                        stack.getDamageValue(), stack.getMaxDamage());
            }
        }
        return 0;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeCatalog> CODEC = new StreamCodec<>() {
        @Override public UpgradeCatalog decode(RegistryFriendlyByteBuf buffer) {
            int rate = buffer.readVarInt();
            int cap = buffer.readVarInt();
            int count = buffer.readVarInt();
            if (count < 1 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid catalogue length");
            var entries = new java.util.ArrayList<Entry>(count);
            for (int i = 0; i < count; i++) entries.add(new Entry(buffer.readResourceLocation(), buffer.readLong()));
            return new UpgradeCatalog(entries, rate, cap);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, UpgradeCatalog data) {
            buffer.writeVarInt(data.returnBasisPoints());
            buffer.writeVarInt(data.capBasisPoints());
            buffer.writeVarInt(data.entries().size());
            for (Entry entry : data.entries()) {
                buffer.writeResourceLocation(entry.id());
                buffer.writeLong(entry.value());
            }
        }
    };
}

