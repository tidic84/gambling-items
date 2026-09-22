package dev.gamblingitems.fabric.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gamblingitems.core.cases.CaseRarity;
import dev.gamblingitems.core.cases.CaseRules;
import dev.gamblingitems.fabric.battle.BattleSettings;
import dev.gamblingitems.fabric.bingo.BingoSettings;
import dev.gamblingitems.fabric.bingo.BingoSetup;
import dev.gamblingitems.fabric.slots.SlotSettings;
import dev.gamblingitems.fabric.slots.SlotSetup;
import dev.gamblingitems.fabric.battle.BattleSetup;
import dev.gamblingitems.fabric.blackjack.BlackjackSettings;
import dev.gamblingitems.fabric.blackjack.BlackjackSetup;
import dev.gamblingitems.fabric.cases.CaseCatalog;
import dev.gamblingitems.fabric.cases.CaseDefinition;
import dev.gamblingitems.fabric.cases.CaseReward;
import dev.gamblingitems.fabric.cases.CaseSetup;
import dev.gamblingitems.fabric.crash.CrashSettings;
import dev.gamblingitems.fabric.crash.CrashSetup;
import dev.gamblingitems.fabric.roulette.RouletteSettings;
import dev.gamblingitems.fabric.roulette.RouletteSetup;
import dev.gamblingitems.fabric.tradeup.TradeUpSettings;
import dev.gamblingitems.fabric.tradeup.TradeUpSetup;
import dev.gamblingitems.fabric.upgrade.UpgradeSetup;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-side settings. Item values are shared by every game; each game keeps its own tuning. */
public final class ModConfig {
    public static final int SCHEMA_VERSION = 16;
    private static ValueCatalog values;
    private static UpgradeSetup upgrader;
    private static TradeUpSetup tradeUp;
    private static CaseSetup cases;
    private static CrashSetup crash;
    private static RouletteSetup roulette;
    private static BlackjackSetup blackjack;
    private static BattleSetup battle;
    private static BingoSetup bingo;
    private static SlotSetup slots;

    private ModConfig() {}

    public static ValueCatalog values() { return require(values); }
    public static UpgradeSetup upgrader() { return require(upgrader); }
    public static TradeUpSetup tradeUp() { return require(tradeUp); }
    public static CaseSetup cases() { return require(cases); }
    public static CrashSetup crash() { return require(crash); }
    public static RouletteSetup roulette() { return require(roulette); }
    public static BlackjackSetup blackjack() { return require(blackjack); }
    public static BattleSetup battle() { return require(battle); }
    public static BingoSetup bingo() { return require(bingo); }
    public static SlotSetup slots() { return require(slots); }

    private static <T> T require(T loaded) {
        if (loaded == null) throw new IllegalStateException("Gambling Items configuration has not loaded");
        return loaded;
    }

    public static void load() {
        Path directory = FabricLoader.getInstance().getConfigDir().resolve("gamblingitems");
        Path path = directory.resolve("games.json");
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(directory);
                write(path, defaults(directory.resolve("upgrader.json")));
            }
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (migrate(json)) write(path, json);
            read(json);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot load " + path + ": " + exception.getMessage(), exception);
        }
    }

    private static void write(Path path, JsonObject json) throws IOException {
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
    }

    /** Brings an older file to the current schema. Custom tuning is kept; obsolete defaults can be migrated. */
    private static boolean migrate(JsonObject json) {
        int schema = json.get("schemaVersion").getAsInt();
        if (schema == SCHEMA_VERSION) return false;
        if (schema < 2 || schema > SCHEMA_VERSION) throw new IllegalArgumentException("Unknown config schema");
        // Schema 2 knew the upgrader and the trade up only; schema 3 added the cases.
        if (schema < 3) json.add("cases", defaultCases());
        if (schema < 4) json.add("crash", defaultCrash());
        // Schema 5 dropped the maximum bet: what a player may engage now depends on their winnings.
        if (schema < 5) json.getAsJsonObject("crash").remove("maximumStake");
        if (schema < 6) json.add("roulette", defaultRoulette());
        // Schema 7: a crash bet is any priced item, so the table no longer names a material and
        // its smallest bet is expressed in value.
        if (schema < 7) {
            JsonObject crashJson = json.getAsJsonObject("crash");
            crashJson.remove("stakeItem");
            crashJson.add("minimumStake", defaultCrash().get("minimumStake"));
        }
        // Schema 8 shortens the old default betting countdown, preserving custom durations.
        if (schema < 8) {
            for (String game : List.of("roulette", "crash")) {
                JsonObject settings = json.getAsJsonObject(game);
                if (settings.get("bettingTicks").getAsInt() == 200) settings.addProperty("bettingTicks", 60);
            }
        }
        // Schema 14 lets the wheel turn three seconds longer, keeping a custom duration.
        if (schema < 14) {
            JsonObject wheel = json.getAsJsonObject("roulette");
            if (wheel.get("spinTicks").getAsInt() == 60) wheel.addProperty("spinTicks", 120);
        }
        // Schema 16 shortens the old bingo countdown to five seconds, keeping a custom one.
        if (schema < 16) {
            JsonObject drum = json.getAsJsonObject("bingo");
            if (drum.get("bettingTicks").getAsInt() == 400) drum.addProperty("bettingTicks", 100);
        }
        // Schema 15 adds the slot machine cabinets.
        if (schema < 15) json.add("slotMachine", defaultSlots());
        // Schema 13 adds the bingo tables.
        if (schema < 13) json.add("bingo", defaultBingo());
        // Schema 12 adds the case battle lobbies.
        if (schema < 12) json.add("caseBattle", defaultBattle());
        // Schema 11 adds the blackjack table.
        if (schema < 11) json.add("blackjack", defaultBlackjack());
        // Schema 10: cases are opened with keys found on mobs, one per rarity.
        if (schema < 10) json.add("cases", migratedCases(json.getAsJsonArray("cases")));
        // Schema 9: the roulette is the real wheel, 0 to 36, and a bet is any priced item.
        // Its payouts are the ones printed on a felt, so the file no longer configures them.
        if (schema < 9) json.add("roulette", defaultRoulette());
        json.addProperty("schemaVersion", SCHEMA_VERSION);
        return true;
    }

    private static void read(JsonObject json) {
        if (json.get("schemaVersion").getAsInt() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unknown config schema");
        }
        var entries = new ArrayList<ValueCatalog.Entry>();
        for (var pair : json.getAsJsonObject("values").entrySet()) {
            entries.add(new ValueCatalog.Entry(item(pair.getKey()),
                    pair.getValue().getAsBigDecimal().longValueExact()));
        }
        entries.sort(Comparator.comparingLong(ValueCatalog.Entry::value).thenComparing(entry -> entry.id().toString()));
        ValueCatalog catalog = new ValueCatalog(entries);
        JsonObject upgraderJson = json.getAsJsonObject("upgrader");
        JsonObject tradeUpJson = json.getAsJsonObject("tradeUp");
        CaseCatalog caseCatalog = readCases(json.getAsJsonArray("cases"), catalog);
        CrashSettings crashSettings = readCrash(json.getAsJsonObject("crash"));
        RouletteSettings rouletteSettings = readRoulette(json.getAsJsonObject("roulette"));
        JsonObject blackjackJson = json.getAsJsonObject("blackjack");
        JsonObject battleJson = json.getAsJsonObject("caseBattle");
        JsonObject bingoJson = json.getAsJsonObject("bingo");
        JsonObject slotsJson = json.getAsJsonObject("slotMachine");
        values = catalog;
        upgrader = new UpgradeSetup(catalog, upgraderJson.get("returnBasisPoints").getAsInt(),
                upgraderJson.get("maximumChanceBasisPoints").getAsInt());
        tradeUp = new TradeUpSetup(catalog, new TradeUpSettings(
                tradeUpJson.get("requiredUnits").getAsInt(),
                tradeUpJson.get("maximumUnitRatioBasisPoints").getAsInt(),
                tradeUpJson.get("returnBasisPoints").getAsInt(),
                tradeUpJson.get("maximumRewardRatioBasisPoints").getAsInt(),
                tradeUpJson.get("rewardCount").getAsInt()));
        cases = new CaseSetup(catalog, caseCatalog);
        bingo = new BingoSetup(catalog, new BingoSettings(
                bingoJson.get("cardPrice").getAsLong(),
                bingoJson.get("returnBasisPoints").getAsInt(),
                bingoJson.get("bettingTicks").getAsInt(),
                bingoJson.get("drawTicks").getAsInt(),
                bingoJson.get("resultTicks").getAsInt()));
        slots = new SlotSetup(catalog, new SlotSettings(
                slotsJson.get("minimumStake").getAsLong(),
                slotsJson.get("returnBasisPoints").getAsInt(),
                slotsJson.get("spinTicks").getAsInt(),
                slotsJson.get("resultTicks").getAsInt()));
        battle = new BattleSetup(cases, new BattleSettings(
                battleJson.get("lobbyTicks").getAsInt(),
                battleJson.get("roundTicks").getAsInt(),
                battleJson.get("resultTicks").getAsInt()));
        crash = new CrashSetup(catalog, crashSettings);
        roulette = new RouletteSetup(catalog, rouletteSettings);
        blackjack = new BlackjackSetup(catalog, new BlackjackSettings(
                blackjackJson.get("minimumStake").getAsLong(),
                blackjackJson.get("dealerDelayTicks").getAsInt(),
                blackjackJson.get("resultTicks").getAsInt()));
    }

    /**
     * Relative weights are normalised once here, so the screen and the draw share the same table.
     * Every price and every reward must be valued, otherwise the announced odds could not be priced.
     */
    private static CaseCatalog readCases(JsonArray casesJson, ValueCatalog catalog) {
        List<CaseDefinition> definitions = new ArrayList<>();
        for (JsonElement element : casesJson) {
            JsonObject caseJson = element.getAsJsonObject();
            String id = caseJson.get("id").getAsString();
            ResourceLocation priceItem = priceItem(caseJson.get("priceItem").getAsString());
            int priceCount = caseJson.get("priceCount").getAsInt();
            // A key has no market value: it is found, not bought. Any other price must be valued.
            if (!isKey(priceItem)) requireValued(catalog, priceItem, priceCount, id);
            JsonArray rewardsJson = caseJson.getAsJsonArray("rewards");
            List<ResourceLocation> items = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            List<Long> rawWeights = new ArrayList<>();
            for (JsonElement rewardElement : rewardsJson) {
                JsonObject rewardJson = rewardElement.getAsJsonObject();
                ResourceLocation rewardItem = item(rewardJson.get("item").getAsString());
                int count = rewardJson.get("count").getAsInt();
                requireValued(catalog, rewardItem, count, id);
                items.add(rewardItem);
                counts.add(count);
                rawWeights.add(rewardJson.get("weight").getAsLong());
            }
            int[] weights = CaseRules.normalize(rawWeights);
            List<CaseReward> rewards = new ArrayList<>(weights.length);
            for (int index = 0; index < weights.length; index++) {
                rewards.add(new CaseReward(items.get(index), counts.get(index), weights[index]));
            }
            definitions.add(new CaseDefinition(id, caseJson.get("name").getAsString(),
                    priceItem, priceCount, rewards));
        }
        return new CaseCatalog(definitions);
    }

    /** The smallest bet is a value, so any priced item may be staked at this table. */
    private static CrashSettings readCrash(JsonObject json) {
        return new CrashSettings(json.get("minimumStake").getAsLong(),
                json.get("returnBasisPoints").getAsInt(), json.get("maximumMultiplier").getAsInt(),
                json.get("growthBasisPoints").getAsInt(),
                json.get("bettingTicks").getAsInt(), json.get("resultTicks").getAsInt());
    }

    /** The smallest bet is a value, so any priced item may be played on the felt. */
    private static RouletteSettings readRoulette(JsonObject json) {
        return new RouletteSettings(json.get("minimumStake").getAsLong(),
                json.get("bettingTicks").getAsInt(), json.get("spinTicks").getAsInt(),
                json.get("resultTicks").getAsInt());
    }

    /** True for one of this mod's keys, the only item of the mod a case may ask for. */
    private static boolean isKey(ResourceLocation id) {
        if (!id.getNamespace().equals("gamblingitems")) return false;
        for (CaseRarity rarity : CaseRarity.values()) {
            if (id.getPath().equals(rarity.keyPath())) return true;
        }
        return false;
    }

    /** The price of a case: any priced item, or a key of this mod. */
    private static ResourceLocation priceItem(String raw) {
        ResourceLocation id = ResourceLocation.parse(raw);
        if (isKey(id)) return id;
        return item(raw);
    }

    private static ResourceLocation item(String raw) {
        ResourceLocation id = ResourceLocation.parse(raw);
        if (!BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id) == Items.AIR
                || id.getNamespace().equals("gamblingitems")) {
            throw new IllegalArgumentException("Unsupported item: " + id);
        }
        return id;
    }

    private static void requireValued(ValueCatalog catalog, ResourceLocation id, int count, String caseId) {
        if (count < 1 || count > 64) throw new IllegalArgumentException("Invalid amount in case " + caseId);
        if (catalog.valueOf(new ItemStack(BuiltInRegistries.ITEM.get(id), count)) <= 0) {
            throw new IllegalArgumentException("Case " + caseId + " uses the unvalued item " + id);
        }
    }

    /** Reuses the values of an existing upgrader-only file, so a running world keeps its prices. */
    private static JsonObject defaults(Path legacy) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonObject upgraderJson = new JsonObject();
        upgraderJson.addProperty("returnBasisPoints", 9000);
        upgraderJson.addProperty("maximumChanceBasisPoints", 9000);
        final JsonObject values;
        if (Files.exists(legacy)) {
            JsonObject old = JsonParser.parseString(Files.readString(legacy)).getAsJsonObject();
            if (old.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("Unknown config schema");
            values = old.getAsJsonObject("values").deepCopy();
            upgraderJson.addProperty("returnBasisPoints", old.get("returnBasisPoints").getAsInt());
            upgraderJson.addProperty("maximumChanceBasisPoints", old.get("maximumChanceBasisPoints").getAsInt());
        } else {
            values = new JsonObject();
            defaultValues().forEach((id, value) -> values.addProperty("minecraft:" + id, value));
        }
        JsonObject tradeUpJson = new JsonObject();
        tradeUpJson.addProperty("requiredUnits", 5);
        tradeUpJson.addProperty("maximumUnitRatioBasisPoints", 12_500);
        tradeUpJson.addProperty("returnBasisPoints", 7_100);
        tradeUpJson.addProperty("maximumRewardRatioBasisPoints", 40_000);
        tradeUpJson.addProperty("rewardCount", 6);
        json.add("values", values);
        json.add("upgrader", upgraderJson);
        json.add("tradeUp", tradeUpJson);
        json.add("cases", defaultCases());
        json.add("crash", defaultCrash());
        json.add("roulette", defaultRoulette());
        json.add("blackjack", defaultBlackjack());
        json.add("caseBattle", defaultBattle());
        json.add("bingo", defaultBingo());
        json.add("slotMachine", defaultSlots());
        return json;
    }

    /** The smallest pull is one iron ingot, and the reels take two seconds to stop. */
    private static JsonObject defaultSlots() {
        JsonObject json = new JsonObject();
        json.addProperty("minimumStake", 1_000);
        json.addProperty("returnBasisPoints", 10_000);
        json.addProperty("spinTicks", 40);
        json.addProperty("resultTicks", 60);
        return json;
    }

    /** A card costs one iron ingot, five seconds to buy one, a number every two seconds. */
    private static JsonObject defaultBingo() {
        JsonObject json = new JsonObject();
        json.addProperty("cardPrice", 1_000);
        json.addProperty("returnBasisPoints", 9_000);
        json.addProperty("bettingTicks", 100);
        json.addProperty("drawTicks", 40);
        json.addProperty("resultTicks", 120);
        return json;
    }

    /** How long a lobby waits for its seats, and how fast the rounds are opened. */
    private static JsonObject defaultBattle() {
        JsonObject json = new JsonObject();
        json.addProperty("lobbyTicks", 1_200);
        json.addProperty("roundTicks", 40);
        json.addProperty("resultTicks", 120);
        return json;
    }

    /** The dealer rules are the printed ones; only the pace and the smallest bet are tuned here. */
    private static JsonObject defaultBlackjack() {
        JsonObject json = new JsonObject();
        // One iron ingot in the default catalogue.
        json.addProperty("minimumStake", 1_000);
        json.addProperty("dealerDelayTicks", 12);
        json.addProperty("resultTicks", 60);
        return json;
    }

    /** The real wheel, 0 to 36. Its payouts are printed on the felt and are not configurable. */
    private static JsonObject defaultRoulette() {
        JsonObject json = new JsonObject();
        // One iron ingot in the default catalogue.
        json.addProperty("minimumStake", 1_000);
        json.addProperty("bettingTicks", 200);
        json.addProperty("spinTicks", 120);
        json.addProperty("resultTicks", 60);
        return json;
    }

    /** Bets in value, a three second betting window and a flight that doubles every two seconds. */
    private static JsonObject defaultCrash() {
        JsonObject json = new JsonObject();
        // One iron ingot in the default catalogue.
        json.addProperty("minimumStake", 1_000);
        json.addProperty("returnBasisPoints", 9_500);
        json.addProperty("maximumMultiplier", 5_000);
        json.addProperty("growthBasisPoints", 10_200);
        json.addProperty("bettingTicks", 60);
        json.addProperty("resultTicks", 100);
        return json;
    }

    /**
     * Keeps whatever an administrator wrote, and makes sure the six key cases exist.
     * The three tables shipped before keys existed are replaced, since nothing could pay for them
     * any more; a table that was edited is kept as it is.
     */
    private static JsonArray migratedCases(JsonArray existing) {
        JsonArray migrated = new JsonArray();
        List<String> shipped = List.of("starter", "miner", "nether");
        for (JsonElement element : existing) {
            JsonObject caseJson = element.getAsJsonObject();
            if (!shipped.contains(caseJson.get("id").getAsString())) migrated.add(caseJson);
        }
        for (JsonElement element : defaultCases()) migrated.add(element);
        return migrated;
    }

    /**
     * One case per key rarity. The weights are relative: an administrator may rewrite them freely,
     * and the odds shown on the screen follow from them.
     */
    private static JsonArray defaultCases() {
        JsonArray cases = new JsonArray();
        cases.add(caseJson(CaseRarity.COMMON, "Common case", new String[][] {
                {"minecraft:coal", "8", "380"},
                {"minecraft:copper_ingot", "16", "260"},
                {"minecraft:iron_ingot", "3", "200"},
                {"minecraft:gold_ingot", "2", "110"},
                {"minecraft:diamond", "1", "45"},
                {"minecraft:emerald_block", "1", "5"}}));
        cases.add(caseJson(CaseRarity.UNCOMMON, "Uncommon case", new String[][] {
                {"minecraft:iron_ingot", "4", "350"},
                {"minecraft:quartz", "5", "250"},
                {"minecraft:gold_ingot", "4", "200"},
                {"minecraft:diamond", "1", "130"},
                {"minecraft:iron_block", "3", "60"},
                {"minecraft:diamond_block", "1", "10"}}));
        cases.add(caseJson(CaseRarity.RARE, "Rare case", new String[][] {
                {"minecraft:gold_ingot", "6", "330"},
                {"minecraft:diamond", "2", "260"},
                {"minecraft:emerald", "5", "200"},
                {"minecraft:diamond_pickaxe", "1", "140"},
                {"minecraft:diamond_block", "1", "60"},
                {"minecraft:netherite_ingot", "1", "10"}}));
        cases.add(caseJson(CaseRarity.EPIC, "Epic case", new String[][] {
                {"minecraft:diamond", "5", "320"},
                {"minecraft:diamond_chestplate", "1", "260"},
                {"minecraft:diamond_block", "1", "220"},
                {"minecraft:netherite_scrap", "3", "150"},
                {"minecraft:netherite_ingot", "1", "45"},
                {"minecraft:nether_star", "1", "5"}}));
        cases.add(caseJson(CaseRarity.LEGENDARY, "Legendary case", new String[][] {
                {"minecraft:diamond_block", "2", "340"},
                {"minecraft:netherite_ingot", "1", "280"},
                {"minecraft:netherite_sword", "1", "200"},
                {"minecraft:netherite_chestplate", "1", "130"},
                {"minecraft:nether_star", "1", "45"},
                {"minecraft:netherite_ingot", "4", "5"}}));
        cases.add(caseJson(CaseRarity.MYTHIC, "Mythic case", new String[][] {
                {"minecraft:netherite_chestplate", "1", "330"},
                {"minecraft:netherite_ingot", "2", "260"},
                {"minecraft:nether_star", "1", "200"},
                {"minecraft:nether_star", "2", "150"},
                {"minecraft:netherite_ingot", "8", "55"},
                {"minecraft:nether_star", "5", "5"}}));
        return cases;
    }

    /** A case of one rarity, opened by the key of that rarity and by nothing else. */
    private static JsonObject caseJson(CaseRarity rarity, String name, String[][] rewards) {
        return caseJson(rarity.caseId(), name, "gamblingitems:" + rarity.keyPath(), 1, rewards);
    }

    private static JsonObject caseJson(String id, String name, String priceItem, int priceCount, String[][] rewards) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("priceItem", priceItem);
        json.addProperty("priceCount", priceCount);
        JsonArray table = new JsonArray();
        for (String[] reward : rewards) {
            JsonObject line = new JsonObject();
            line.addProperty("item", reward[0]);
            line.addProperty("count", Integer.parseInt(reward[1]));
            line.addProperty("weight", Long.parseLong(reward[2]));
            table.add(line);
        }
        json.add("rewards", table);
        return json;
    }

    private static Map<String, Long> defaultValues() {
        Map<String, Long> items = new LinkedHashMap<>();
        items.put("copper_ingot", 100L);
        items.put("coal", 150L);
        items.put("redstone", 200L);
        items.put("lapis_lazuli", 250L);
        items.put("iron_ingot", 1000L);
        items.put("quartz", 1200L);
        items.put("gold_ingot", 2000L);
        items.put("amethyst_shard", 2200L);
        items.put("iron_sword", 2500L);
        items.put("iron_pickaxe", 3500L);
        items.put("ender_pearl", 4000L);
        items.put("emerald", 5000L);
        items.put("blaze_rod", 6000L);
        items.put("iron_block", 9000L);
        items.put("diamond", 10000L);
        items.put("gold_block", 18000L);
        items.put("diamond_sword", 20500L);
        items.put("diamond_pickaxe", 30500L);
        items.put("diamond_boots", 40000L);
        items.put("emerald_block", 45000L);
        items.put("diamond_helmet", 50000L);
        items.put("netherite_scrap", 60000L);
        items.put("diamond_leggings", 70000L);
        items.put("diamond_chestplate", 80000L);
        items.put("diamond_block", 90000L);
        items.put("netherite_ingot", 248000L);
        items.put("netherite_sword", 280000L);
        items.put("netherite_pickaxe", 290000L);
        items.put("netherite_chestplate", 350000L);
        items.put("nether_star", 500000L);
        return items;
    }
}
