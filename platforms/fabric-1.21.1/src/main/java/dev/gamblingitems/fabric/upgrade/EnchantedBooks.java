package dev.gamblingitems.fabric.upgrade;

import com.google.gson.JsonArray;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Explicitly priced books belong to the upgrader, outside the recipe-derived item economy. */
public final class EnchantedBooks {
    private EnchantedBooks() {}

    public static ValueCatalog add(ValueCatalog base, JsonArray books, RegistryAccess registries) {
        var entries = new ArrayList<>(base.entries());
        for (var element : books) {
            var book = element.getAsJsonObject();
            ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
            var enchantments = book.getAsJsonObject("enchantments");
            if (enchantments.size() == 0) throw new IllegalArgumentException("An enchanted book needs enchantments");
            for (var pair : enchantments.entrySet()) {
                ResourceLocation id = ResourceLocation.parse(pair.getKey());
                int level = pair.getValue().getAsBigDecimal().intValueExact();
                //#if MC >= 1.21
                var enchantment = registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                        .getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, id));
                int maximum = enchantment.value().getMaxLevel();
                //#else
                //$ var enchantment = net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.getOptional(id)
                //$         .orElseThrow(() -> new IllegalArgumentException("Unknown enchantment: " + id));
                //$ int maximum = enchantment.getMaxLevel();
                //#endif
                if (level < 1 || level > maximum) throw new IllegalArgumentException("Invalid level for " + id + ": " + level);
                //#if MC >= 1.20.5
                EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(enchantment, level));
                //#else
                //$ net.minecraft.world.item.EnchantedBookItem.addEnchantment(stack,
                //$         new net.minecraft.world.item.enchantment.EnchantmentInstance(enchantment, level));
                //#endif
            }
            entries.add(new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("enchanted_book"),
                    book.get("value").getAsBigDecimal().longValueExact(), stack));
        }
        entries.sort(Comparator.comparingLong(ValueCatalog.Entry::value).thenComparing(entry -> entry.id().toString()));
        return new ValueCatalog(entries);
    }

    /** Books have the same vanilla hover name; include their localized enchantments in lists and search. */
    public static Component displayName(ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK)) return stack.getHoverName();
        var name = Component.empty();
        //#if MC >= 1.20.5
        var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet();
        //#else
        //$ var enchantments = EnchantmentHelper.getEnchantments(stack).entrySet();
        //#endif
        for (var enchantment : enchantments) {
            //#if MC >= 1.20.5
            var type = enchantment.getKey();
            int level = enchantment.getIntValue();
            //#else
            //$ var type = enchantment.getKey();
            //$ int level = enchantment.getValue();
            //#endif
            //#if MC >= 1.21
            if (!name.getSiblings().isEmpty()) name.append(" · ");
            name.append(Enchantment.getFullname(type, level));
            //#else
            //$ if (!name.getSiblings().isEmpty()) name.append(" · ");
            //$ name.append(type.getFullname(level));
            //#endif
        }
        return name.getSiblings().isEmpty() ? stack.getHoverName() : name;
    }
}
