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
        return JsonParser.parseString("{\"schemaVersion\": 19, \"slotMachine\": {\"minimumStake\": 4242}, \"upgrader\": {},"
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
        helper.assertTrue(json.getAsJsonObject("upgrader").has("enchantedBooks"), "The 0.2.2 enchanted books are added");
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

    @GameTest(template = EMPTY_STRUCTURE)
    public void schema22GainsTheTableGamesAndTheVipCardOnly(GameTestHelper helper) {
        JsonObject json = JsonParser.parseString("{\"schemaVersion\": 22, \"upgrader\": {},"
                + " \"machines\": {\"plinko\": {\"minimumStake\": 777}},"
                + " \"arcade\": {\"prizes\": [{\"item\": \"minecraft:cake\", \"count\": 1, \"tickets\": 5}]},"
                + " \"currency\": {\"enabled\": true, \"games\": [\"slotMachine\"]}}").getAsJsonObject();
        helper.assertTrue(ModConfig.migrate(json), "A schema 22 file is migrated");
        JsonObject machines = json.getAsJsonObject("machines");
        for (String key : new String[] {"craps", "baccarat", "keno", "scratchCard"}) {
            helper.assertTrue(machines.has(key), "The table game is added: " + key);
            helper.assertTrue(json.getAsJsonObject("currency").getAsJsonArray("games").contains(new JsonPrimitive(key)),
                    "And paid in the currency that pays the slot machine: " + key);
        }
        helper.assertTrue(machines.getAsJsonObject("plinko").get("minimumStake").getAsInt() == 777, "A tuned machine is untouched");
        JsonArray prizes = json.getAsJsonObject("arcade").getAsJsonArray("prizes");
        helper.assertTrue(prizes.size() == 2 && prizes.get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:cake")
                        && prizes.get(1).getAsJsonObject().get("item").getAsString().equals("gamblingitems:vip_card"),
                "The owner's shelf is kept, the VIP card added at its end");
        helper.succeed();
    }
}
