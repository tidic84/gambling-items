package dev.gamblingitems.fabric.value;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.gamblingitems.core.value.RecipeValues;
import dev.gamblingitems.fabric.item.KeyItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.DecoratedPotRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingRecipe;

/** Reads vanilla and mod recipes after datapacks load and exports the provenance of each price. */
public final class RecipeValuation {
    private RecipeValuation() {}
    public record Result(ValueCatalog catalog, JsonObject report) {}

    public static Result build(MinecraftServer server, ValueCatalog configured, JsonObject settings) {
        long fallback = settings.get("fallbackValue").getAsBigDecimal().longValueExact();
        if (fallback < 1 || fallback > ValueCatalog.MAX_VALUE) throw new IllegalArgumentException("Invalid fallbackValue");
        var namespaceValues = new HashMap<String, Long>();
        for (var entry : settings.getAsJsonObject("namespaceFallbackValues").entrySet()) {
            long value = entry.getValue().getAsBigDecimal().longValueExact();
            if (value < 1 || value > ValueCatalog.MAX_VALUE) throw new IllegalArgumentException("Invalid namespace value");
            namespaceValues.put(entry.getKey(), value);
        }
        var excluded = new HashSet<String>();
        for (var id : settings.getAsJsonArray("excludedItems")) excluded.add(ResourceLocation.parse(id.getAsString()).toString());
        var explicit = new HashMap<String, Long>();
        for (var entry : configured.entries()) if (!excluded.contains(entry.id().toString())) explicit.put(entry.id().toString(), entry.value());
        var recipes = new ArrayList<RecipeValues.Recipe>();
        JsonArray skipped = new JsonArray();
        if (settings.get("enabled").getAsBoolean()) {
            // Stable recipe order makes conversion cycles and reports reproducible.
            var holders = server.getRecipeManager().getRecipes().stream()
                    .sorted(Comparator.comparing(holder -> holder.id().toString())).toList();
            for (var holder : holders) {
                try {
                    var recipe = holder.value();
                    ItemStack output = recipe.getResultItem(server.registryAccess());
                    if (recipe instanceof DecoratedPotRecipe) output = new ItemStack(Items.DECORATED_POT);
                    if (!plain(output) || excluded.contains(id(output))) { skipped.add(holder.id().toString()); continue; }
                    var ingredients = new ArrayList<List<String>>();
                    List<Ingredient> inputs = recipe.getIngredients();
                    if (recipe instanceof SmithingRecipe smithing) {
                        // Smithing exposes predicates instead of the normal ingredient list.
                        // Reading those also supports transform recipes added by other mods.
                        inputs = List.of(matching(smithing::isTemplateIngredient), matching(smithing::isBaseIngredient),
                                matching(smithing::isAdditionIngredient));
                    } else if (recipe instanceof DecoratedPotRecipe) {
                        inputs = java.util.Collections.nCopies(4, Ingredient.of(Items.BRICK));
                    }
                    for (var ingredient : inputs) {
                        if (ingredient.isEmpty()) continue; // Empty cells of shaped recipes.
                        var alternatives = new ArrayList<String>();
                        for (ItemStack stack : ingredient.getItems()) {
                            if (plain(stack) && !excluded.contains(id(stack))) alternatives.add(id(stack));
                        }
                        ingredients.add(alternatives);
                    }
                    if (ingredients.stream().anyMatch(List::isEmpty)) {
                        skipped.add(holder.id().toString());
                        continue;
                    }
                    if (ingredients.isEmpty()) {
                        skipped.add(holder.id().toString());
                        // A custom machine can expose its output without standard ingredients.
                        // Keep that craftable target, marked as an estimate in the report.
                        recipes.add(new RecipeValues.Recipe(id(output), 1, List.of(List.of(id(output)))));
                    } else {
                        recipes.add(new RecipeValues.Recipe(id(output), output.getCount(), ingredients));
                    }
                } catch (RuntimeException exception) {
                    skipped.add(holder.id() + ": " + exception.getClass().getSimpleName());
                }
            }
        }
        var resolved = RecipeValues.resolve(explicit, recipes,
                id -> namespaceValues.getOrDefault(ResourceLocation.parse(id).getNamespace(), fallback), ValueCatalog.MAX_VALUE);
        var entries = resolved.values().entrySet().stream()
                .map(entry -> new ValueCatalog.Entry(ResourceLocation.parse(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingLong(ValueCatalog.Entry::value).thenComparing(entry -> entry.id().toString())).toList();
        JsonObject report = new JsonObject();
        report.addProperty("converged", resolved.converged());
        report.addProperty("itemCount", entries.size());
        report.add("unsupportedRecipes", skipped);
        JsonObject items = new JsonObject();
        for (var entry : entries) {
            String id = entry.id().toString();
            JsonObject item = new JsonObject();
            item.addProperty("value", entry.value());
            item.addProperty("source", explicit.containsKey(id) ? "configured"
                    : resolved.fallbackItems().contains(id) ? "fallback" : "recipe");
            items.add(id, item);
        }
        report.add("items", items);
        return new Result(new ValueCatalog(entries), report);
    }

    private static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static Ingredient matching(java.util.function.Predicate<ItemStack> predicate) {
        return Ingredient.of(BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(predicate));
    }
    private static boolean plain(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof KeyItem)
                && ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem()));
    }
}
