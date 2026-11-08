package dev.gamblingitems.fabric.config;

import com.google.gson.JsonParser;
import dev.gamblingitems.fabric.upgrade.EnchantedBooks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class EnchantedBookConfigGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void migrationAddsBooksAndPreservesCustomSettings(GameTestHelper helper) {
        var json = JsonParser.parseString("""
                {"schemaVersion":19,"values":{"minecraft:diamond":12345},
                 "currency":{"enabled":false},
                 "upgrader":{"returnBasisPoints":8123,"maximumChanceBasisPoints":7654}}
                """).getAsJsonObject();
        helper.assertTrue(ModConfig.migrate(json), "Old configuration migrates");
        var upgrader = json.getAsJsonObject("upgrader");
        helper.assertTrue(upgrader.getAsJsonArray("enchantedBooks").size() == 8, "Default books are added");
        helper.assertTrue(upgrader.get("returnBasisPoints").getAsInt() == 8123
                && upgrader.get("maximumChanceBasisPoints").getAsInt() == 7654
                && json.getAsJsonObject("values").get("minecraft:diamond").getAsInt() == 12345,
                "Custom odds and prices are preserved");
        helper.assertFalse(ModConfig.migrate(json), "Migration is idempotent");
        json.addProperty("schemaVersion", 19);
        upgrader.add("enchantedBooks", new com.google.gson.JsonArray());
        ModConfig.migrate(json);
        helper.assertTrue(upgrader.getAsJsonArray("enchantedBooks").isEmpty(), "An explicit empty list is preserved");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void invalidBookDefinitionsAreRejected(GameTestHelper helper) {
        String[] definitions = {
            "[{\"enchantments\":{},\"value\":1000}]",
            "[{\"enchantments\":{\"minecraft:missing_enchantment\":1},\"value\":1000}]",
            "[{\"enchantments\":{\"minecraft:sharpness\":6},\"value\":1000}]",
            "[{\"enchantments\":{\"minecraft:mending\":0},\"value\":1000}]",
            "[{\"enchantments\":{\"minecraft:mending\":1.5},\"value\":1000}]",
            "[{\"enchantments\":{\"minecraft:mending\":1},\"value\":0}]",
            "[{\"enchantments\":{\"minecraft:mending\":1},\"value\":1000000001}]",
            "[{\"enchantments\":{\"minecraft:mending\":1},\"value\":1000},"
                + "{\"enchantments\":{\"minecraft:mending\":1},\"value\":2000}]"
        };
        for (String definition : definitions) {
            boolean rejected = false;
            try {
                EnchantedBooks.add(ModConfig.values(), JsonParser.parseString(definition).getAsJsonArray(),
                        helper.getLevel().registryAccess());
            } catch (RuntimeException expected) { rejected = true; }
            helper.assertTrue(rejected, "Reject invalid definition: " + definition);
        }
        helper.assertTrue(EnchantedBooks.add(ModConfig.values(), new com.google.gson.JsonArray(),
                helper.getLevel().registryAccess()).entries().equals(ModConfig.values().entries()),
                "Empty list leaves the base catalogue intact");
        helper.succeed();
    }
}
