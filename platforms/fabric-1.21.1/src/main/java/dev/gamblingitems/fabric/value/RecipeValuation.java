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
//#if MC < 1.21.2
import net.minecraft.world.item.crafting.DecoratedPotRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingRecipe;
//#endif

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
            for (var read : read(server)) {
                try {
                    ItemStack output = read.output();
                    if (!plain(output) || excluded.contains(id(output))) { skipped.add(read.id()); continue; }
                    var ingredients = new ArrayList<List<String>>();
                    for (var alternatives : read.inputs()) {
                        if (alternatives.isEmpty()) continue; // Empty cells of shaped recipes.
                        var ids = new ArrayList<String>();
                        for (ItemStack stack : alternatives) if (plain(stack) && !excluded.contains(id(stack))) ids.add(id(stack));
                        ingredients.add(ids);
                    }
                    if (ingredients.stream().anyMatch(List::isEmpty)) {
                        skipped.add(read.id());
                        continue;
                    }
                    if (ingredients.isEmpty()) {
                        skipped.add(read.id());
                        // A custom machine can expose its output without standard ingredients.
                        // Keep that craftable target, marked as an estimate in the report.
                        recipes.add(new RecipeValues.Recipe(id(output), 1, List.of(List.of(id(output)))));
                    } else {
                        recipes.add(new RecipeValues.Recipe(id(output), output.getCount(), ingredients));
                    }
                } catch (RuntimeException exception) {
                    skipped.add(read.id() + ": " + exception.getClass().getSimpleName());
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

    /** One recipe as the valuation needs it: what it makes, and the alternatives of each input. */
    private record Read(String id, ItemStack output, List<List<ItemStack>> inputs) {}

    /** Every recipe of the server, in a stable order so conversion cycles and reports are reproducible. */
    private static List<Read> read(MinecraftServer server) {
        var reads = new ArrayList<Read>();
        //#if MC >= 1.20.2
        var holders = server.getRecipeManager().getRecipes().stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString())).toList();
        //#else
        //$ // Before 1.20.2 a recipe carried its own id, without a holder around it.
        //$ var holders = server.getRecipeManager().getRecipes().stream()
        //$         .sorted(Comparator.comparing(recipe -> recipe.getId().toString())).toList();
        //#endif
        //#if MC >= 1.21.2
        //$ // Recipes describe themselves through displays since 1.21.2, the same ones the recipe book shows.
        //$ // Read while the server starts, before any level exists: the context is built from the server itself.
        //#if MC >= 26.3
        //$ var context = net.minecraft.util.context.ContextMap.builder()
        //$         .set(net.minecraft.world.item.crafting.display.SlotDisplayContext.REGISTRIES, server.registryAccess())
        //$         .buildAndValidate(net.minecraft.world.item.crafting.display.SlotDisplayContext.CONTEXT);
        //#else
        //$ var context = new net.minecraft.util.context.ContextMap.Builder()
        //$         .withParameter(net.minecraft.world.item.crafting.display.SlotDisplayContext.FUEL_VALUES, server.fuelValues())
        //$         .withParameter(net.minecraft.world.item.crafting.display.SlotDisplayContext.REGISTRIES, server.registryAccess())
        //$         .create(net.minecraft.world.item.crafting.display.SlotDisplayContext.CONTEXT);
        //#endif
        //$ for (var holder : holders) {
        //$     String name = holder.id().toString();
        //$     try {
        //$         for (var display : holder.value().display()) {
        //$             List<net.minecraft.world.item.crafting.display.SlotDisplay> slots;
        //$             if (display instanceof net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay shaped) slots = shaped.ingredients();
        //$             else if (display instanceof net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay shapeless) slots = shapeless.ingredients();
        //$             else if (display instanceof net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay furnace) slots = List.of(furnace.ingredient());
        //$             else if (display instanceof net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay cutter) slots = List.of(cutter.input());
        //$             else if (display instanceof net.minecraft.world.item.crafting.display.SmithingRecipeDisplay smithing) slots = List.of(smithing.template(), smithing.base(), smithing.addition());
        //$             else { reads.add(new Read(name, ItemStack.EMPTY, List.of())); continue; }
        //$             var inputs = new ArrayList<List<ItemStack>>();
        //$             for (var slot : slots) inputs.add(slot.resolveForStacks(context));
        //$             reads.add(new Read(name, display.result().resolveForFirstStack(context), inputs));
        //$         }
        //$     } catch (RuntimeException exception) {
        //$         reads.add(new Read(name + ": " + exception.getClass().getSimpleName(), ItemStack.EMPTY, List.of()));
        //$     }
        //$ }
        //#else
        for (var holder : holders) {
            //#if MC >= 1.20.2
            var recipe = holder.value();
            String name = holder.id().toString();
            //#else
            //$ var recipe = holder;
            //$ String name = holder.getId().toString();
            //#endif
            try {
                ItemStack output = recipe.getResultItem(server.registryAccess());
                if (recipe instanceof DecoratedPotRecipe) output = new ItemStack(Items.DECORATED_POT);
                List<Ingredient> ingredients = recipe.getIngredients();
                if (recipe instanceof SmithingRecipe smithing) {
                    // Smithing exposes predicates instead of the normal ingredient list.
                    // Reading those also supports transform recipes added by other mods.
                    ingredients = List.of(matching(smithing::isTemplateIngredient), matching(smithing::isBaseIngredient),
                            matching(smithing::isAdditionIngredient));
                } else if (recipe instanceof DecoratedPotRecipe) {
                    ingredients = java.util.Collections.nCopies(4, Ingredient.of(Items.BRICK));
                }
                var inputs = new ArrayList<List<ItemStack>>();
                for (var ingredient : ingredients) inputs.add(ingredient.isEmpty() ? List.of() : List.of(ingredient.getItems()));
                reads.add(new Read(name, output, inputs));
            } catch (RuntimeException exception) {
                reads.add(new Read(name + ": " + exception.getClass().getSimpleName(), ItemStack.EMPTY, List.of()));
            }
        }
        //#endif
        return reads;
    }

    private static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    //#if MC < 1.21.2
    private static Ingredient matching(java.util.function.Predicate<ItemStack> predicate) {
        return Ingredient.of(BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(predicate));
    }
    //#endif
    private static boolean plain(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof KeyItem)
                && ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem()));
    }
}
