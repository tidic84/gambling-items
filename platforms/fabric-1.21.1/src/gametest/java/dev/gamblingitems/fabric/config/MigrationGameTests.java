package dev.gamblingitems.fabric.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** A schema 19 file gains the prize machines without losing what its owner tuned. */
public class MigrationGameTests implements FabricGameTest {
    private static JsonObject schema19(String games) {
        return JsonParser.parseString("{\"schemaVersion\": 19, \"slotMachine\": {\"minimumStake\": 4242},"
                + " \"currency\": {\"enabled\": true, \"games\": " + games + "}}").getAsJsonObject();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theMachinesJoinACurrencyThatPaysTheSlotMachine(GameTestHelper helper) {
        JsonObject json = schema19("[\"slotMachine\", \"crash\"]");
        helper.assertTrue(ModConfig.migrate(json), "A schema 19 file is migrated");
        helper.assertTrue(json.get("schemaVersion").getAsInt() == ModConfig.SCHEMA_VERSION, "To the current schema");
        for (String key : ModConfig.MACHINE_KEYS.values()) {
            helper.assertTrue(json.getAsJsonObject("machines").has(key), "Every machine has its section: " + key);
        }
        helper.assertTrue(json.has("arcade") && json.getAsJsonObject("arcade").get("tickets").getAsBoolean(),
                "The arcade tickets are added, on");
        JsonArray games = json.getAsJsonObject("currency").getAsJsonArray("games");
        helper.assertTrue(games.contains(new JsonPrimitive("plinko"))
                && games.contains(new JsonPrimitive("luckyWheel")) && games.contains(new JsonPrimitive("crash")),
                "The machines are paid in the currency, the other games kept");
        helper.assertTrue(json.getAsJsonObject("slotMachine").get("minimumStake").getAsInt() == 4242,
                "A tuned value is untouched");
        helper.assertFalse(ModConfig.migrate(json), "A current file is left alone");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aCurrencyWithoutTheSlotMachineStaysAsItWas(GameTestHelper helper) {
        JsonObject json = schema19("[\"crash\"]");
        ModConfig.migrate(json);
        helper.assertTrue(json.getAsJsonObject("currency").getAsJsonArray("games").size() == 1,
                "The games the currency was given are kept as they were");
        helper.succeed();
    }
}
