package dev.gamblingitems.core.value;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeValuesTest {
    private RecipeValues.Recipe recipe(String output, int count, String... ingredients) {
        return new RecipeValues.Recipe(output, count, java.util.Arrays.stream(ingredients).map(List::of).toList());
    }

    @Test void resolvesChainedRecipesAndOutputCountsRegardlessOfOrder() {
        var result = RecipeValues.resolve(Map.of("log", 160L), List.of(
                recipe("stick", 4, "plank", "plank"), recipe("plank", 4, "log")), id -> 1000, 1_000_000);
        assertEquals(40L, result.values().get("plank"));
        assertEquals(20L, result.values().get("stick"));
        assertTrue(result.converged());
        assertTrue(result.fallbackItems().isEmpty());
    }

    @Test void picksCheapestAlternativeAndCheapestRecipe() {
        var tagRecipe = new RecipeValues.Recipe("gear", 1, List.of(List.of("iron", "copper"), List.of("iron", "copper")));
        var result = RecipeValues.resolve(Map.of("iron", 1000L, "copper", 100L),
                List.of(recipe("gear", 1, "iron"), tagRecipe), id -> 1000, 1_000_000);
        assertEquals(200L, result.values().get("gear"));
    }

    @Test void preservesManualModdedPricesAndPropagatesThem() {
        var result = RecipeValues.resolve(Map.of("mod:ingot", 2400L, "mod:gear", 12000L),
                List.of(recipe("mod:gear", 1, "mod:ingot"), recipe("mod:machine", 1, "mod:gear", "mod:gear")),
                id -> 1000, 1_000_000);
        assertEquals(12000L, result.values().get("mod:gear"));
        assertEquals(24000L, result.values().get("mod:machine"));
    }

    @Test void handlesUnpricedRawMaterialsAndReportsEstimates() {
        var result = RecipeValues.resolve(Map.of(), List.of(recipe("mod:gear", 1, "mod:ore", "mod:ore")),
                id -> 750, 1_000_000);
        assertEquals(1500L, result.values().get("mod:gear"));
        assertTrue(result.fallbackItems().contains("mod:ore"));
        assertFalse(result.fallbackItems().contains("mod:gear"));
    }

    @Test void unseededCyclesTerminateAndHaveEditableFallbacks() {
        var result = RecipeValues.resolve(Map.of(), List.of(recipe("a", 1, "b"), recipe("b", 1, "a")),
                id -> 500, 1_000_000);
        assertEquals(Map.of("a", 500L, "b", 500L), result.values());
        assertTrue(result.converged());
    }

    @Test void conversionCyclesNeverCreateZeroOrNegativeValues() {
        var result = RecipeValues.resolve(Map.of("seed", 100L), List.of(recipe("a", 1, "seed"),
                recipe("b", 2, "a"), recipe("a", 2, "b")), id -> 1000, 1_000_000);
        assertEquals(1L, result.values().get("a"));
        assertEquals(1L, result.values().get("b"));
        assertTrue(result.converged());
    }

    @Test void aSelfReferencingTemplateDoesNotGiveItsToolsAFallbackPrice() {
        var result = RecipeValues.resolve(Map.of("diamond", 10000L, "ingot", 248000L, "axe", 30500L),
                List.of(recipe("template", 2, "template", "diamond"),
                        recipe("upgraded_axe", 1, "template", "ingot", "axe")), id -> 100, 1_000_000_000L);
        assertEquals(278600L, result.values().get("upgraded_axe"));
        assertFalse(result.fallbackItems().contains("upgraded_axe"));
        assertTrue(result.converged());
    }

    @Test void costsDoNotOverflowAndAreClampedToTheCatalogLimit() {
        var result = RecipeValues.resolve(Map.of("a", Long.MAX_VALUE),
                List.of(recipe("b", 1, "a", "a")), id -> 1000, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, result.values().get("b"));
    }
}
