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
    private final java.util.Map<ResourceLocation, List<Entry>> byId;

    public record Entry(ResourceLocation id, long value, ItemStack template) {
        public Entry(ResourceLocation id, long value) {
            this(id, value, new ItemStack(dev.gamblingitems.fabric.Compat.item(id)));
        }
        public Entry {
            if (value <= 0 || value > MAX_VALUE) throw new IllegalArgumentException("Invalid item value");
            if (template.isEmpty() || template.getCount() != 1
                    || !BuiltInRegistries.ITEM.getKey(template.getItem()).equals(id))
                throw new IllegalArgumentException("Invalid catalogue stack");
            template = template.copy();
        }
        @Override public ItemStack template() { return template.copy(); }
        public ItemStack stack() { return template.copy(); }
        public net.minecraft.network.chat.Component displayName() {
            return dev.gamblingitems.fabric.upgrade.EnchantedBooks.displayName(template);
        }
        @Override public boolean equals(Object other) {
            return other instanceof Entry entry && id.equals(entry.id) && value == entry.value
                    && ItemStack.isSameItemSameComponents(template, entry.template);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, value); }
    }

    public ValueCatalog(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        if (entries.isEmpty() || entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid item value catalogue");
        }
        var index = new java.util.HashMap<ResourceLocation, List<Entry>>();
        for (Entry entry : entries) {
            var variants = index.computeIfAbsent(entry.id(), ignored -> new java.util.ArrayList<>());
            if (variants.stream().anyMatch(existing -> ItemStack.isSameItemSameComponents(existing.template, entry.template)))
                throw new IllegalArgumentException("Duplicate catalogue stack: " + entry.id());
            variants.add(entry);
        }
        index.replaceAll((id, variants) -> List.copyOf(variants));
        byId = java.util.Map.copyOf(index);
    }

    public List<Entry> entries() { return entries; }

    /** Value of the whole stack, or 0 when no configured variant matches its data. */
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
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        // Only explicitly priced variants match, including every component of enchanted books.
        for (Entry entry : byId.getOrDefault(id, List.of())) {
            if (ItemStack.isSameItemSameComponents(normalized, entry.template)) return entry;
        }
        return null;
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
                long value = buffer.readVarLong();
                ItemStack stack = new ItemStack(item);
                if (buffer.readBoolean()) {
                    //#if MC >= 1.20.5
                    stack = ItemStack.STREAM_CODEC.decode(buffer);
                    //#else
                    //$ stack = buffer.readItem();
                    //#endif
                }
                entries.add(new Entry(BuiltInRegistries.ITEM.getKey(item), value, stack));
            }
            return new ValueCatalog(entries);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, ValueCatalog data) {
            buffer.writeVarInt(data.entries().size());
            for (Entry entry : data.entries()) {
                buffer.writeVarInt(BuiltInRegistries.ITEM.getId(dev.gamblingitems.fabric.Compat.item(entry.id())));
                buffer.writeVarLong(entry.value());
                boolean variant = !ItemStack.isSameItemSameComponents(entry.template, new ItemStack(entry.template.getItem()));
                buffer.writeBoolean(variant);
                if (variant) {
                    //#if MC >= 1.20.5
                    ItemStack.STREAM_CODEC.encode(buffer, entry.template);
                    //#else
                    //$ buffer.writeItem(entry.template);
                    //#endif
                }
            }
        }
    };
}
