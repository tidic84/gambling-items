package dev.gamblingitems.fabric.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gamblingitems.core.cases.CaseRules;
import dev.gamblingitems.fabric.cases.CaseCatalog;
import dev.gamblingitems.fabric.cases.CaseDefinition;
import dev.gamblingitems.fabric.cases.CaseReward;
import dev.gamblingitems.fabric.cases.CaseSetup;
import dev.gamblingitems.fabric.crash.CrashSettings;
import dev.gamblingitems.fabric.crash.CrashSetup;
import dev.gamblingitems.fabric.roulette.RouletteSettings;
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
    public static final int SCHEMA_VERSION = 8;
    private static ValueCatalog values;
    private static UpgradeSetup upgrader;
    private static TradeUpSetup tradeUp;
    private static CaseSetup cases;
    private static CrashSetup crash;
    private static RouletteSettings roulette;

    private ModConfig() {}

    public static ValueCatalog values() { return require(values); }
    public static UpgradeSetup upgrader() { return require(upgrader); }
    public static TradeUpSetup tradeUp() { return require(tradeUp); }
    public static CaseSetup cases() { return require(cases); }
    public static CrashSetup crash() { return require(crash); }
    public static RouletteSettings roulette() { return require(roulette); }

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
        crash = new CrashSetup(catalog, crashSettings);
        roulette = rouletteSettings;
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
            ResourceLocation priceItem = item(caseJson.get("priceItem").getAsString());
            int priceCount = caseJson.get("priceCount").getAsInt();
            requireValued(catalog, priceItem, priceCount, id);
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

    /** The wheel itself is validated by the pure rules; the table must also be payable. */
    private static RouletteSettings readRoulette(JsonObject json) {
        RouletteSettings settings = new RouletteSettings(item(json.get("stakeItem").getAsString()),
                json.get("minimumStake").getAsInt(),
                json.get("redSlots").getAsInt(), json.get("blackSlots").getAsInt(),
                json.get("greenSlots").getAsInt(),
                json.get("colourPayout").getAsInt(), json.get("greenPayout").getAsInt(),
                json.get("bettingTicks").getAsInt(), json.get("spinTicks").getAsInt(),
                json.get("resultTicks").getAsInt());
        if (!settings.isPayable()) {
            throw new IllegalArgumentException("A roulette table whose smallest bet could not be paid");
        }
        return settings;
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
        return json;
    }

    /** The short wheel proposed in the design: seven red, seven black, one green paying fourteen. */
    private static JsonObject defaultRoulette() {
        JsonObject json = new JsonObject();
        json.addProperty("stakeItem", "minecraft:gold_ingot");
        json.addProperty("minimumStake", 1);
        json.addProperty("redSlots", 7);
        json.addProperty("blackSlots", 7);
        json.addProperty("greenSlots", 1);
        json.addProperty("colourPayout", 2);
        json.addProperty("greenPayout", 14);
        json.addProperty("bettingTicks", 60);
        json.addProperty("spinTicks", 60);
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
     * Starting tables, balanced against the default values to return roughly nine tenths of the price.
     * Weights are relative: an administrator may rewrite them freely, the odds shown follow.
     */
    private static JsonArray defaultCases() {
        JsonArray cases = new JsonArray();
        cases.add(caseJson("starter", "Starter case", "minecraft:iron_ingot", 1, new String[][] {
                {"minecraft:coal", "4", "440"},
                {"minecraft:redstone", "4", "220"},
                {"minecraft:copper_ingot", "8", "150"},
                {"minecraft:lapis_lazuli", "6", "100"},
                {"minecraft:iron_ingot", "2", "80"},
                {"minecraft:emerald", "1", "10"}}));
        cases.add(caseJson("miner", "Miner case", "minecraft:iron_ingot", 5, new String[][] {
                {"minecraft:coal", "8", "220"},
                {"minecraft:copper_ingot", "16", "200"},
                {"minecraft:iron_ingot", "4", "350"},
                {"minecraft:gold_ingot", "3", "150"},
                {"minecraft:diamond", "1", "70"},
                {"minecraft:diamond_block", "1", "10"}}));
        cases.add(caseJson("nether", "Nether case", "minecraft:gold_block", 2, new String[][] {
                {"minecraft:blaze_rod", "3", "380"},
                {"minecraft:quartz", "24", "250"},
                {"minecraft:ender_pearl", "6", "250"},
                {"minecraft:netherite_scrap", "1", "100"},
                {"minecraft:netherite_ingot", "1", "18"},
                {"minecraft:nether_star", "1", "2"}}));
        return cases;
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
