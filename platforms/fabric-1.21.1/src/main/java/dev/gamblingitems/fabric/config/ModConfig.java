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
import com.google.gson.JsonPrimitive;
import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.machines.MachineSettings;
import dev.gamblingitems.fabric.machines.MachineSetup;
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
import dev.gamblingitems.fabric.platform.Platform;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-side settings. Item values are shared by every game; each game keeps its own tuning. */
public final class ModConfig {
    public static final int SCHEMA_VERSION = 23;
    /** One displayed unit of currency, so every screen counts in the currency's own units. */
    public static final long CURRENCY_UNIT = 1_000;
    /** The casino games that can be switched to a currency. Upgrader, trade up, cases and battles never are. */
    public static final List<String> CURRENCY_GAMES = List.of("crash", "roulette", "blackjack", "bingo", "slotMachine",
            "luckyWheel", "coinPusher", "prizeClaw", "pachinko", "capsuleGacha", "highStakes",
            "plinko", "bigSix", "videoPoker", "horseRace", "craps", "baccarat", "keno", "scratchCard");
    /** The key of each prize machine under "machines", which is also its name among the currency games. */
    public static final Map<GameMode, String> MACHINE_KEYS = machineKeys();
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
    private static Map<GameMode, MachineSetup> machines;
    private static dev.gamblingitems.fabric.arcade.ArcadeSettings arcade;

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
    public static MachineSetup machine(GameMode mode) { return require(require(machines).get(mode)); }
    public static dev.gamblingitems.fabric.arcade.ArcadeSettings arcade() { return require(arcade); }

    private static Map<GameMode, String> machineKeys() {
        Map<GameMode, String> keys = new java.util.EnumMap<>(GameMode.class);
        keys.put(GameMode.LUCKY_WHEEL, "luckyWheel");
        keys.put(GameMode.COIN_PUSHER, "coinPusher");
        keys.put(GameMode.PRIZE_CLAW, "prizeClaw");
        keys.put(GameMode.PACHINKO, "pachinko");
        keys.put(GameMode.CAPSULE_GACHA, "capsuleGacha");
        keys.put(GameMode.HIGH_STAKES, "highStakes");
        keys.put(GameMode.PLINKO, "plinko");
        keys.put(GameMode.BIG_SIX, "bigSix");
        keys.put(GameMode.VIDEO_POKER, "videoPoker");
        keys.put(GameMode.HORSE_RACE, "horseRace");
        keys.put(GameMode.CRAPS, "craps");
        keys.put(GameMode.BACCARAT, "baccarat");
        keys.put(GameMode.KENO, "keno");
        keys.put(GameMode.SCRATCH_CARD, "scratchCard");
        return java.util.Collections.unmodifiableMap(keys);
    }

    private static <T> T require(T loaded) {
        if (loaded == null) throw new IllegalStateException("Gambling Items configuration has not loaded");
        return loaded;
    }

    public static void load(net.minecraft.server.MinecraftServer server) {
        Path directory = Platform.configDir().resolve("gamblingitems");
        Path path = directory.resolve("games.json");
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(directory);
                write(path, defaults(directory.resolve("upgrader.json")));
            }
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (migrate(json)) write(path, json);
            read(json, server, directory);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot load " + path + ": " + exception.getMessage(), exception);
        }
    }

    private static void write(Path path, JsonObject json) throws IOException {
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
    }

    /** Brings an older file to the current schema. Custom tuning is kept; obsolete defaults can be migrated. */
    static boolean migrate(JsonObject json) {
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
        // Steps run in ascending order: a later step may read a section an earlier one added.
        // Schema 9: the roulette is the real wheel, 0 to 36, and a bet is any priced item.
        // Its payouts are the ones printed on a felt, so the file no longer configures them.
        if (schema < 9) json.add("roulette", defaultRoulette());
        // Schema 10: cases are opened with keys found on mobs, one per rarity.
        if (schema < 10) json.add("cases", migratedCases(json.getAsJsonArray("cases")));
        // Schema 11 adds the blackjack table.
        if (schema < 11) json.add("blackjack", defaultBlackjack());
        // Schema 12 adds the case battle lobbies.
        if (schema < 12) json.add("caseBattle", defaultBattle());
        // Schema 13 adds the bingo tables.
        if (schema < 13) json.add("bingo", defaultBingo());
        // Schema 14 lets the wheel turn three seconds longer, keeping a custom duration.
        if (schema < 14) {
            JsonObject wheel = json.getAsJsonObject("roulette");
            if (wheel.get("spinTicks").getAsInt() == 60) wheel.addProperty("spinTicks", 120);
        }
        // Schema 15 adds the slot machine cabinets.
        if (schema < 15) json.add("slotMachine", defaultSlots());
        // Schema 16 shortens the old bingo countdown to five seconds, keeping a custom one.
        if (schema < 16) {
            JsonObject drum = json.getAsJsonObject("bingo");
            if (drum.get("bettingTicks").getAsInt() == 400) drum.addProperty("bettingTicks", 100);
        }
        if (schema < 17) {
            json.add("automaticValues", defaultAutomaticValues());
            // Only fill new base materials; never overwrite the server owner's prices.
            JsonObject configured = json.getAsJsonObject("values");
            baseValues().forEach((id, value) -> {
                if (!configured.has("minecraft:" + id)) configured.addProperty("minecraft:" + id, value);
            });
        }
        // Schema 18 adds an optional currency for the casino games, off by default.
        if (schema < 18) json.add("currency", defaultCurrency());
        // Schema 19: a currency may have several items, each with its own worth. The single item
        // of schema 18 becomes the unit.
        if (schema < 19) {
            JsonObject currency = json.getAsJsonObject("currency");
            if (currency.has("item")) {
                JsonObject items = new JsonObject();
                items.addProperty(currency.remove("item").getAsString(), 1);
                currency.add("items", items);
            }
        }
        // Schema 20 (0.2.2) adds the enchanted books to the upgrader. Checked on every older file: unreleased
        // builds numbered the machines 20 to 22 before 0.2.2 took 20.
        if (!json.getAsJsonObject("upgrader").has("enchantedBooks"))
            json.getAsJsonObject("upgrader").add("enchantedBooks", defaultEnchantedBooks());
        // Schema 21 adds the prize machines. A currency that already pays the slot machine pays them
        // too, since they are played the same way; otherwise the games it was given are kept as they were.
        if (schema < 21) {
            json.add("machines", defaultMachines());
            JsonArray games = json.getAsJsonObject("currency").getAsJsonArray("games");
            if (games != null && games.contains(new JsonPrimitive("slotMachine"))) {
                for (String key : List.of("luckyWheel", "coinPusher", "prizeClaw", "pachinko", "capsuleGacha", "highStakes")) games.add(key);
            }
        }
        // Schema 22 opens the arcade: tickets, chips and a prize counter, and four more machines.
        // The cabinets now pay in tickets; "arcade.tickets": false brings back the payouts in items.
        if (schema < 22) {
            json.add("arcade", defaultArcade());
            JsonObject machines = json.getAsJsonObject("machines");
            JsonObject added = defaultMachines();
            for (String key : List.of("plinko", "bigSix", "videoPoker", "horseRace")) {
                if (!machines.has(key)) machines.add(key, added.get(key));
            }
            JsonArray games = json.getAsJsonObject("currency").getAsJsonArray("games");
            if (games != null && games.contains(new JsonPrimitive("slotMachine"))) {
                for (String key : List.of("plinko", "bigSix", "videoPoker", "horseRace")) {
                    if (!games.contains(new JsonPrimitive(key))) games.add(key);
                }
            }
        }
        // Schema 23 adds the table games and the VIP card on the prize shelf; nothing configured is changed.
        if (schema < 23) {
            JsonObject machines = json.getAsJsonObject("machines");
            JsonObject added = defaultMachines();
            List<String> tableGames = List.of("craps", "baccarat", "keno", "scratchCard");
            for (String key : tableGames) {
                if (!machines.has(key)) machines.add(key, added.get(key));
            }
            JsonArray games = json.getAsJsonObject("currency").getAsJsonArray("games");
            if (games != null && games.contains(new JsonPrimitive("slotMachine"))) {
                for (String key : tableGames) {
                    if (!games.contains(new JsonPrimitive(key))) games.add(key);
                }
            }
            JsonArray prizes = json.getAsJsonObject("arcade").getAsJsonArray("prizes");
            boolean hasCard = false;
            for (JsonElement prize : prizes) hasCard |= VIP_CARD.equals(prize.getAsJsonObject().get("item").getAsString());
            if (!hasCard) prizes.add(shelfPrize(VIP_CARD, 1, VIP_CARD_TICKETS));
        }
        json.addProperty("schemaVersion", SCHEMA_VERSION);
        return true;
    }

    private static void read(JsonObject json, net.minecraft.server.MinecraftServer server, Path directory) throws IOException {
        if (json.get("schemaVersion").getAsInt() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unknown config schema");
        }
        var entries = new ArrayList<ValueCatalog.Entry>();
        for (var pair : json.getAsJsonObject("values").entrySet()) {
            entries.add(new ValueCatalog.Entry(item(pair.getKey()),
                    pair.getValue().getAsBigDecimal().longValueExact()));
        }
        entries.sort(Comparator.comparingLong(ValueCatalog.Entry::value).thenComparing(entry -> entry.id().toString()));
        var valuation = dev.gamblingitems.fabric.value.RecipeValuation.build(server, new ValueCatalog(entries),
                json.getAsJsonObject("automaticValues"));
        var arcadeSettings = readArcade(json.getAsJsonObject("arcade"));
        ValueCatalog catalog = withChips(valuation.catalog(), arcadeSettings.chipValue());
        write(directory.resolve("resolved-values.json"), valuation.report());
        long ticketValue = arcadeSettings.tickets() ? arcadeSettings.ticketValue() : 0;
        JsonObject upgraderJson = json.getAsJsonObject("upgrader");
        JsonObject tradeUpJson = json.getAsJsonObject("tradeUp");
        CaseCatalog caseCatalog = readCases(json.getAsJsonArray("cases"), catalog);
        CrashSettings crashSettings = readCrash(json.getAsJsonObject("crash"));
        RouletteSettings rouletteSettings = readRoulette(json.getAsJsonObject("roulette"));
        JsonObject blackjackJson = json.getAsJsonObject("blackjack");
        JsonObject battleJson = json.getAsJsonObject("caseBattle");
        JsonObject bingoJson = json.getAsJsonObject("bingo");
        JsonObject slotsJson = json.getAsJsonObject("slotMachine");
        Map<String, ValueCatalog> casino = readCurrency(json.getAsJsonObject("currency"), catalog);
        JsonObject machinesJson = json.getAsJsonObject("machines");
        if (machinesJson == null) throw new IllegalArgumentException("Missing \"machines\"");
        Map<GameMode, MachineSetup> machineSetups = new java.util.EnumMap<>(GameMode.class);
        MACHINE_KEYS.forEach((mode, key) -> machineSetups.put(mode,
                new MachineSetup(casino.get(key), readMachine(machinesJson, mode, key), ticketValue)));
        values = catalog;
        arcade = arcadeSettings;
        upgrader = new UpgradeSetup(dev.gamblingitems.fabric.upgrade.EnchantedBooks.add(catalog,
                upgraderJson.getAsJsonArray("enchantedBooks"), server.registryAccess()), upgraderJson.get("returnBasisPoints").getAsInt(),
                upgraderJson.get("maximumChanceBasisPoints").getAsInt());
        tradeUp = new TradeUpSetup(catalog, new TradeUpSettings(
                tradeUpJson.get("requiredUnits").getAsInt(),
                tradeUpJson.get("maximumUnitRatioBasisPoints").getAsInt(),
                tradeUpJson.get("returnBasisPoints").getAsInt(),
                tradeUpJson.get("maximumRewardRatioBasisPoints").getAsInt(),
                tradeUpJson.get("rewardCount").getAsInt()));
        cases = new CaseSetup(catalog, caseCatalog);
        bingo = new BingoSetup(casino.get("bingo"), new BingoSettings(
                bingoJson.get("cardPrice").getAsLong(),
                bingoJson.get("returnBasisPoints").getAsInt(),
                bingoJson.get("bettingTicks").getAsInt(),
                bingoJson.get("drawTicks").getAsInt(),
                bingoJson.get("resultTicks").getAsInt()));
        machines = machineSetups;
        slots = new SlotSetup(casino.get("slotMachine"), new SlotSettings(
                slotsJson.get("minimumStake").getAsLong(),
                slotsJson.get("returnBasisPoints").getAsInt(),
                slotsJson.get("spinTicks").getAsInt(),
                slotsJson.get("resultTicks").getAsInt()), ticketValue);
        battle = new BattleSetup(cases, new BattleSettings(
                battleJson.get("lobbyTicks").getAsInt(),
                battleJson.get("roundTicks").getAsInt(),
                battleJson.get("resultTicks").getAsInt()));
        crash = new CrashSetup(casino.get("crash"), crashSettings);
        roulette = new RouletteSetup(casino.get("roulette"), rouletteSettings);
        blackjack = new BlackjackSetup(casino.get("blackjack"), new BlackjackSettings(
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

    /**
     * The catalogue each casino game plays with. Without a currency it is the shared one: any priced
     * item is a stake and a win comes back as catalogue items. With a currency, the listed games know
     * only its items, each worth its configured units of {@link #CURRENCY_UNIT}: nothing else can be
     * staked and every win is paid in them, dearest coin first, never in random items.
     */
    private static Map<String, ValueCatalog> readCurrency(JsonObject json, ValueCatalog shared) {
        Map<String, ValueCatalog> catalogs = new LinkedHashMap<>();
        CURRENCY_GAMES.forEach(game -> catalogs.put(game, shared));
        if (!json.get("enabled").getAsBoolean()) return catalogs;
        List<ValueCatalog.Entry> coins = new ArrayList<>();
        for (var pair : json.getAsJsonObject("items").entrySet()) {
            // Worth is written in displayed units, so 0.5 is half a unit; finer than 0.001 cannot be held.
            java.math.BigDecimal units = pair.getValue().getAsBigDecimal();
            long worth;
            try {
                worth = units.multiply(java.math.BigDecimal.valueOf(CURRENCY_UNIT)).longValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Invalid currency value for " + pair.getKey() + ": " + units);
            }
            if (worth <= 0 || worth > ValueCatalog.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid currency value for " + pair.getKey() + ": " + units);
            }
            coins.add(new ValueCatalog.Entry(item(pair.getKey()), worth));
        }
        if (coins.isEmpty()) throw new IllegalArgumentException("An enabled currency needs at least one item");
        // Change is made from the cheapest coin upwards, as the shared catalogue is.
        coins.sort(Comparator.comparingLong(ValueCatalog.Entry::value).thenComparing(entry -> entry.id().toString()));
        ValueCatalog currency = new ValueCatalog(coins);
        for (JsonElement element : json.getAsJsonArray("games")) {
            String game = element.getAsString();
            if (!CURRENCY_GAMES.contains(game)) {
                throw new IllegalArgumentException("Unknown currency game: " + game + ", expected one of " + CURRENCY_GAMES);
            }
            catalogs.put(game, currency);
        }
        return catalogs;
    }

    /**
     * The casino chips join the shared catalogue at their worth, unless an administrator priced them
     * in "values" already: they can then be staked anywhere a priced item can.
     */
    private static ValueCatalog withChips(ValueCatalog catalog, long chipValue) {
        List<ValueCatalog.Entry> entries = new ArrayList<>(catalog.entries());
        for (int chip : dev.gamblingitems.fabric.arcade.ArcadeSettings.CHIPS) {
            ResourceLocation id = dev.gamblingitems.fabric.ModContent.id("chip_" + chip);
            if (entries.stream().noneMatch(entry -> entry.id().equals(id))) entries.add(new ValueCatalog.Entry(id, chipValue * chip));
        }
        entries.sort(Comparator.comparingLong(ValueCatalog.Entry::value).thenComparing(entry -> entry.id().toString()));
        return new ValueCatalog(entries);
    }

    /** The arcade: what a ticket and a chip are worth, how long a race takes bets, and the prize counter. */
    private static dev.gamblingitems.fabric.arcade.ArcadeSettings readArcade(JsonObject json) {
        if (json == null) throw new IllegalArgumentException("Missing \"arcade\"");
        List<dev.gamblingitems.fabric.arcade.ArcadeSettings.Prize> prizes = new ArrayList<>();
        JsonArray shelf = json.getAsJsonArray("prizes");
        if (shelf == null) throw new IllegalArgumentException("Missing arcade.prizes");
        for (int index = 0; index < shelf.size(); index++) {
            JsonObject prize = shelf.get(index).getAsJsonObject();
            String where = "arcade.prizes[" + index + "]";
            ResourceLocation id = ResourceLocation.parse(required(prize, "item", where).getAsString());
            if (!BuiltInRegistries.ITEM.containsKey(id) || dev.gamblingitems.fabric.Compat.item(id) == Items.AIR) {
                throw new IllegalArgumentException(where + ": unknown item " + id);
            }
            prizes.add(new dev.gamblingitems.fabric.arcade.ArcadeSettings.Prize(id,
                    required(prize, "count", where).getAsInt(), required(prize, "tickets", where).getAsLong()));
        }
        return new dev.gamblingitems.fabric.arcade.ArcadeSettings(required(json, "tickets", "arcade").getAsBoolean(),
                required(json, "ticketValue", "arcade").getAsLong(), required(json, "chipValue", "arcade").getAsLong(),
                required(json, "raceBettingTicks", "arcade").getAsInt(), prizes);
    }

    /**
     * A ticket is worth a hundredth of an iron ingot, so a win counts in hundreds of tickets, and a
     * chip of 1 an iron ingot. The prize counter asks for each prize the tickets of its value.
     */
    private static final String VIP_CARD = "gamblingitems:vip_card";
    private static final int VIP_CARD_TICKETS = 25_000;

    private static JsonObject shelfPrize(String item, int count, int tickets) {
        JsonObject prize = new JsonObject();
        prize.addProperty("item", item);
        prize.addProperty("count", count);
        prize.addProperty("tickets", tickets);
        return prize;
    }

    private static JsonObject defaultArcade() {
        JsonObject json = new JsonObject();
        json.addProperty("tickets", true);
        json.addProperty("ticketValue", 10);
        json.addProperty("chipValue", 1_000);
        json.addProperty("raceBettingTicks", 200);
        JsonArray prizes = new JsonArray();
        Object[][] shelf = {{"minecraft:iron_ingot", 1, 100}, {"minecraft:cookie", 16, 150}, {"minecraft:gold_ingot", 1, 200},
                {"minecraft:firework_rocket", 16, 300}, {"minecraft:ender_pearl", 1, 400}, {"minecraft:emerald", 1, 500},
                {"minecraft:name_tag", 1, 800}, {"minecraft:diamond", 1, 1_000}, {"minecraft:saddle", 1, 1_200},
                {"minecraft:jukebox", 1, 1_200}, {"minecraft:music_disc_cat", 1, 1_500}, {"minecraft:golden_apple", 1, 1_700},
                {"minecraft:totem_of_undying", 1, 6_000}, {"minecraft:enchanted_golden_apple", 1, 20_000},
                {"minecraft:netherite_ingot", 1, 24_800}, {VIP_CARD, 1, VIP_CARD_TICKETS}, {"minecraft:nether_star", 1, 50_000}};
        for (Object[] entry : shelf) prizes.add(shelfPrize((String) entry[0], (Integer) entry[1], (Integer) entry[2]));
        json.add("prizes", prizes);
        return json;
    }

    /** Disabled: the casino plays with every priced item until an administrator names a currency. */
    private static JsonObject defaultCurrency() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", false);
        JsonObject items = new JsonObject();
        items.addProperty("minecraft:emerald", 1);
        json.add("items", items);
        JsonArray games = new JsonArray();
        CURRENCY_GAMES.forEach(games::add);
        json.add("games", games);
        return json;
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
        if (!BuiltInRegistries.ITEM.containsKey(id) || dev.gamblingitems.fabric.Compat.item(id) == Items.AIR
                || dev.gamblingitems.fabric.Compat.item(id) instanceof dev.gamblingitems.fabric.item.KeyItem) {
            throw new IllegalArgumentException("Unsupported item: " + id);
        }
        return id;
    }

    private static void requireValued(ValueCatalog catalog, ResourceLocation id, int count, String caseId) {
        if (count < 1 || count > 64) throw new IllegalArgumentException("Invalid amount in case " + caseId);
        if (catalog.valueOf(new ItemStack(dev.gamblingitems.fabric.Compat.item(id), count)) <= 0) {
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
        upgraderJson.add("enchantedBooks", defaultEnchantedBooks());
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
        json.add("automaticValues", defaultAutomaticValues());
        json.add("upgrader", upgraderJson);
        json.add("tradeUp", tradeUpJson);
        json.add("cases", defaultCases());
        json.add("crash", defaultCrash());
        json.add("roulette", defaultRoulette());
        json.add("blackjack", defaultBlackjack());
        json.add("caseBattle", defaultBattle());
        json.add("bingo", defaultBingo());
        json.add("slotMachine", defaultSlots());
        json.add("machines", defaultMachines());
        json.add("currency", defaultCurrency());
        json.add("arcade", defaultArcade());
        return json;
    }

    /**
     * One prize machine. Multipliers are written as they are read, 1.5 for one and a half times the
     * stake, stake included; a claw grip is a percentage. A mistake names the machine and the field.
     */
    private static MachineSettings readMachine(JsonObject machines, GameMode mode, String key) {
        String where = "machines." + key;
        JsonObject json = machines.getAsJsonObject(key);
        if (json == null) throw new IllegalArgumentException("Missing " + where);
        try {
            int[] multipliers, weights;
            switch (mode) {
                case PACHINKO, PLINKO -> {
                    multipliers = hundredths(json.getAsJsonArray("pockets"), where + ".pockets");
                    weights = new int[0];
                }
                case VIDEO_POKER, KENO -> {
                    multipliers = hundredths(json.getAsJsonArray("paytable"), where + ".paytable");
                    weights = new int[0];
                }
                case CRAPS, BACCARAT -> {
                    multipliers = hundredths(json.getAsJsonArray("payouts"), where + ".payouts");
                    weights = new int[0];
                }
                case HIGH_STAKES -> {
                    multipliers = hundredths(json.getAsJsonArray("targets"), where + ".targets");
                    weights = new int[0];
                }
                default -> {
                    JsonArray prizes = json.getAsJsonArray("prizes");
                    if (prizes == null) throw new IllegalArgumentException("Missing " + where + ".prizes");
                    multipliers = new int[prizes.size()];
                    weights = new int[prizes.size()];
                    for (int index = 0; index < prizes.size(); index++) {
                        JsonObject prize = prizes.get(index).getAsJsonObject();
                        String at = where + ".prizes[" + index + "]";
                        multipliers[index] = hundredth(prize.get("multiplier"), at + ".multiplier");
                        weights[index] = mode == GameMode.PRIZE_CLAW ? hundredth(prize.get("gripPercent"), at + ".gripPercent")
                                : required(prize, "weight", at).getAsInt();
                    }
                }
            }
            return new MachineSettings(required(json, "enabled", where).getAsBoolean(),
                    required(json, "minimumStake", where).getAsLong(), required(json, "maximumStake", where).getAsLong(),
                    required(json, "returnBasisPoints", where).getAsInt(), required(json, "playTicks", where).getAsInt(),
                    required(json, "resultTicks", where).getAsInt(),
                    new dev.gamblingitems.core.machines.MachineTable(mode, multipliers, weights));
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            throw new IllegalArgumentException(message.startsWith(where) ? message : where + ": " + message, exception);
        }
    }

    private static JsonElement required(JsonObject json, String field, String where) {
        JsonElement value = json.get(field);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("Missing " + where + "." + field);
        return value;
    }

    private static int[] hundredths(JsonArray array, String where) {
        if (array == null) throw new IllegalArgumentException("Missing " + where);
        int[] values = new int[array.size()];
        for (int index = 0; index < values.length; index++) values[index] = hundredth(array.get(index), where + "[" + index + "]");
        return values;
    }

    /** 1.5 is read as 150; anything finer than a hundredth is refused rather than rounded. */
    private static int hundredth(JsonElement element, String where) {
        if (element == null) throw new IllegalArgumentException("Missing " + where);
        try {
            return element.getAsBigDecimal().movePointRight(2).intValueExact();
        } catch (ArithmeticException | NumberFormatException | UnsupportedOperationException | IllegalStateException exception) {
            throw new IllegalArgumentException(where + " must be a number with at most two decimals, not " + element);
        }
    }

    private static JsonObject machine(long minimumStake, int returnBasisPoints, int playTicks) {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", true);
        json.addProperty("minimumStake", minimumStake);
        json.addProperty("maximumStake", 0);
        json.addProperty("returnBasisPoints", returnBasisPoints);
        json.addProperty("playTicks", playTicks);
        json.addProperty("resultTicks", 60);
        return json;
    }

    /** Prizes as {multiplier, weight} pairs, or {multiplier, grip in percent} for a claw. */
    private static JsonArray prizes(String weightName, String[][] prizes) {
        JsonArray array = new JsonArray();
        for (String[] prize : prizes) {
            JsonObject entry = new JsonObject();
            entry.add("multiplier", new JsonPrimitive(new java.math.BigDecimal(prize[0])));
            entry.add(weightName, new JsonPrimitive(new java.math.BigDecimal(prize[1])));
            array.add(entry);
        }
        return array;
    }

    private static JsonArray decimals(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(new JsonPrimitive(new java.math.BigDecimal(value)));
        return array;
    }

    /**
     * The default machines give back between 95 and 96.25% of what they take, computed exactly from
     * their tables (MachineTableTest checks them). The smallest bet is one iron ingot, a diamond at
     * the high stakes terminal.
     */
    private static JsonObject defaultMachines() {
        JsonObject machines = new JsonObject();
        // Twelve sectors of unequal size: the jackpot is a sliver, the blanks are wide.
        JsonObject wheel = machine(1_000, 10_000, 100);
        wheel.add("prizes", prizes("weight", new String[][] {{"0", "10"}, {"1.5", "8"}, {"0", "10"}, {"2", "6"},
                {"0.5", "8"}, {"5", "2"}, {"0", "10"}, {"1.5", "8"}, {"0.5", "8"}, {"3", "4"}, {"0", "10"}, {"15", "1"}}));
        machines.add("luckyWheel", wheel);
        // What a push tips over the edge, as a share of the stake.
        JsonObject pusher = machine(1_000, 10_000, 60);
        pusher.add("prizes", prizes("weight", new String[][] {{"0", "57"}, {"0.5", "18"}, {"1", "12"}, {"2", "7"},
                {"5", "3"}, {"10", "2"}, {"25", "1"}}));
        machines.add("coinPusher", pusher);
        // Seven positions: the heavier the prize, the looser the grip.
        JsonObject claw = machine(1_000, 10_000, 100);
        claw.add("prizes", prizes("gripPercent", new String[][] {{"1.5", "64"}, {"3", "32"}, {"8", "12"},
                {"25", "3.8"}, {"3", "32"}, {"1.5", "64"}, {"5", "19.2"}}));
        machines.add("prizeClaw", claw);
        JsonObject pachinko = machine(1_000, 10_000, 80);
        pachinko.add("pockets", decimals("5.6", "2.1", "1.1", "1", "0.4", "1", "1.1", "2.1", "5.6"));
        machines.add("pachinko", pachinko);
        // Five capsule colours, from the common white to the legendary gold.
        JsonObject gacha = machine(1_000, 10_000, 70);
        gacha.add("prizes", prizes("weight", new String[][] {{"0.3", "600"}, {"1", "250"}, {"2", "110"},
                {"5", "35"}, {"25", "5"}}));
        machines.add("capsuleGacha", gacha);
        // Every target is fair before the return rate: 2x is reached once in two plays.
        JsonObject highStakes = machine(10_000, 9_600, 50);
        highStakes.add("targets", decimals("1.25", "1.5", "2", "3", "5", "10", "25", "100"));
        machines.add("highStakes", highStakes);
        // Twelve rows of pins: the edges are rare and pay big, the middle gives back less than the stake.
        JsonObject plinko = machine(1_000, 10_000, 100);
        plinko.add("pockets", decimals("41", "11", "4", "1.8", "1.1", "0.5", "0.4", "0.5", "1.1", "1.8", "4", "11", "41"));
        machines.add("plinko", plinko);
        // The real big six wheel: 54 segments, the bet on a symbol paid as printed on its layout.
        JsonObject bigSix = machine(1_000, 10_000, 120);
        bigSix.add("prizes", prizes("weight", new String[][] {{"2", "24"}, {"3", "15"}, {"6", "7"}, {"11", "4"},
                {"21", "2"}, {"41", "1"}, {"41", "1"}}));
        machines.add("bigSix", bigSix);
        // Jacks or better, 9/6: the paytable stake included, from a pair of jacks to the royal flush.
        JsonObject poker = machine(1_000, 9_600, 30);
        poker.add("paytable", decimals("1", "2", "3", "4", "6", "9", "25", "50", "800"));
        machines.add("videoPoker", poker);
        // Six horses: the stronger they are, the more often they win and the less they pay.
        JsonObject race = machine(1_000, 10_000, 200);
        race.add("prizes", prizes("weight", new String[][] {{"2.7", "35"}, {"3.8", "25"}, {"5.2", "18"}, {"7.9", "12"},
                {"13.5", "7"}, {"31", "3"}}));
        machines.add("horseRace", race);
        // The table games keep their own house edge, as printed in a casino: pass and don't pass pay even money.
        JsonObject craps = machine(1_000, 10_000, 110);
        craps.add("payouts", decimals("2", "2"));
        machines.add("craps", craps);
        // Player, banker (5% commission) and tie at 8 to 1.
        JsonObject baccarat = machine(1_000, 10_000, 120);
        baccarat.add("payouts", decimals("2", "1.95", "9"));
        machines.add("baccarat", baccarat);
        // Five spots, paid by hits from none to five: about 95.1%.
        JsonObject keno = machine(1_000, 10_000, 100);
        keno.add("paytable", decimals("0", "0", "0.5", "3", "20", "500"));
        machines.add("keno", keno);
        // Three of a prize on the card win it; the blank stars never do. About 95%.
        JsonObject scratch = machine(1_000, 10_000, 80);
        scratch.add("prizes", prizes("weight", new String[][] {{"0", "4640"}, {"1", "2000"}, {"2", "1000"}, {"5", "400"},
                {"20", "60"}, {"100", "5"}}));
        machines.add("scratchCard", scratch);
        return machines;
    }

    private static JsonArray defaultEnchantedBooks() {
        JsonArray books = new JsonArray();
        String[] ids = {"mending", "sharpness", "unbreaking", "efficiency", "fortune", "silk_touch", "looting", "protection"};
        int[] levels = {1, 5, 3, 5, 3, 1, 3, 4};
        long[] prices = {80_000, 60_000, 40_000, 60_000, 60_000, 50_000, 60_000, 50_000};
        for (int i = 0; i < ids.length; i++) {
            JsonObject book = new JsonObject();
            JsonObject enchantments = new JsonObject();
            enchantments.addProperty("minecraft:" + ids[i], levels[i]);
            book.add("enchantments", enchantments);
            book.addProperty("value", prices[i]);
            books.add(book);
        }
        return books;
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
        items.putAll(baseValues());
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

    private static JsonObject defaultAutomaticValues() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", true);
        json.addProperty("fallbackValue", 1000);
        JsonObject namespaces = new JsonObject();
        namespaces.addProperty("minecraft", 100);
        json.add("namespaceFallbackValues", namespaces);
        json.add("excludedItems", new JsonArray());
        return json;
    }

    private static Map<String, Long> baseValues() {
        Map<String, Long> items = new LinkedHashMap<>();
        for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry")) {
            items.put(wood + "_log", 160L);
            items.put("stripped_" + wood + "_log", 160L);
            items.put("stripped_" + wood + "_wood", 213L);
        }
        items.put("crimson_stem", 160L);
        items.put("warped_stem", 160L);
        items.put("stripped_crimson_stem", 160L);
        items.put("stripped_warped_stem", 160L);
        items.put("stripped_crimson_hyphae", 213L);
        items.put("stripped_warped_hyphae", 213L);
        for (String item : List.of("cobblestone", "cobbled_deepslate", "dirt", "sand", "red_sand", "gravel", "netherrack")) {
            items.put(item, 20L);
        }
        for (String item : List.of("wheat", "sugar_cane", "bamboo", "kelp")) items.put(item, 40L);
        for (String item : List.of("string", "leather", "feather", "bone", "clay_ball", "slime_ball")) items.put(item, 100L);
        return items;
    }
}
