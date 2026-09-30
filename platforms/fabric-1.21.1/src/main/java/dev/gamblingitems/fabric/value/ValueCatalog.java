package dev.gamblingitems.fabric.value;

import dev.gamblingitems.core.upgrade.ItemValues;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Immutable server snapshot of item values, shared by every game and sent when a menu opens. */
public final class ValueCatalog {
    // Selection uses Minecraft's signed 16-bit container data. The final id is the spin action.
    public static final int MAX_ENTRIES = 32_767;
    public static final long MAX_VALUE = 1_000_000_000L;
    private final List<Entry> entries;
    private final java.util.Map<ResourceLocation, Entry> byId;

    public record Entry(ResourceLocation id, long value) {
        public Entry {
            if (value <= 0 || value > MAX_VALUE) throw new IllegalArgumentException("Invalid item value");
        }
        public ItemStack stack() { return new ItemStack(BuiltInRegistries.ITEM.get(id)); }
    }

    public ValueCatalog(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        if (entries.isEmpty() || entries.size() > MAX_ENTRIES
                || entries.stream().map(Entry::id).distinct().count() != entries.size()) {
            throw new IllegalArgumentException("Invalid item value catalogue");
        }
        var index = new java.util.HashMap<ResourceLocation, Entry>();
        for (Entry entry : entries) index.put(entry.id(), entry);
        byId = java.util.Map.copyOf(index);
    }

    public List<Entry> entries() { return entries; }

    /** Value of the whole stack, or 0 when the item is unlisted or carries extra data. */
    public long valueOf(ItemStack stack) {
        Entry entry = entryFor(stack);
        return entry == null ? 0
                : ItemValues.stackValue(entry.value(), stack.getCount(), stack.getDamageValue(), stack.getMaxDamage());
    }

    /** Value of a single item of this stack, used where each unit is compared separately. */
    public long unitValue(ItemStack stack) {
        Entry entry = entryFor(stack);
        return entry == null ? 0
                : ItemValues.stackValue(entry.value(), 1, stack.getDamageValue(), stack.getMaxDamage());
    }

    private Entry entryFor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ItemStack normalized = stack.copy();
        if (normalized.isDamageableItem()) normalized.setDamageValue(0);
        // Enchantments, custom names and container contents are not priced: refuse the item.
        if (!ItemStack.isSameItemSameComponents(normalized, new ItemStack(stack.getItem()))) return null;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return byId.get(id);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ValueCatalog> CODEC = new StreamCodec<>() {
        @Override public ValueCatalog decode(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 1 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid catalogue length");
            var entries = new java.util.ArrayList<Entry>(count);
            for (int i = 0; i < count; i++) {
                int itemId = buffer.readVarInt();
                var item = BuiltInRegistries.ITEM.byId(itemId);
                if (item == null || item == net.minecraft.world.item.Items.AIR) {
                    throw new IllegalArgumentException("Invalid catalogue item");
                }
                entries.add(new Entry(BuiltInRegistries.ITEM.getKey(item), buffer.readVarLong()));
            }
            return new ValueCatalog(entries);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, ValueCatalog data) {
            buffer.writeVarInt(data.entries().size());
            for (Entry entry : data.entries()) {
                buffer.writeVarInt(BuiltInRegistries.ITEM.getId(BuiltInRegistries.ITEM.get(entry.id())));
                buffer.writeVarLong(entry.value());
            }
        }
    };
}
