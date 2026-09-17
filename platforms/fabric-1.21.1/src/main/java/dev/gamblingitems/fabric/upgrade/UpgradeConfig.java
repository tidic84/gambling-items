package dev.gamblingitems.fabric.upgrade;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

public final class UpgradeConfig {
    private static UpgradeCatalog current;
    private UpgradeConfig() {}

    public static UpgradeCatalog current() {
        if (current == null) throw new IllegalStateException("Upgrader configuration has not loaded");
        return current;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("gamblingitems/upgrader.json");
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(defaults()),
                        StandardCharsets.UTF_8);
            }
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (json.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("Unknown config schema");
            var entries = new ArrayList<UpgradeCatalog.Entry>();
            for (var pair : json.getAsJsonObject("values").entrySet()) {
                ResourceLocation id = ResourceLocation.parse(pair.getKey());
                if (!BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id) == Items.AIR
                        || id.getNamespace().equals("gamblingitems")) {
                    throw new IllegalArgumentException("Unsupported item: " + id);
                }
                long value = pair.getValue().getAsBigDecimal().longValueExact();
                entries.add(new UpgradeCatalog.Entry(id, value));
            }
            entries.sort(Comparator.comparingLong(UpgradeCatalog.Entry::value).thenComparing(e -> e.id().toString()));
            current = new UpgradeCatalog(entries, json.get("returnBasisPoints").getAsInt(),
                    json.get("maximumChanceBasisPoints").getAsInt());
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot load " + path + ": " + exception.getMessage(), exception);
        }
    }

    private static JsonObject defaults() {
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", 1);
        json.addProperty("returnBasisPoints", 9000);
        json.addProperty("maximumChanceBasisPoints", 9000);
        JsonObject values = new JsonObject();
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
        items.forEach((id, value) -> values.addProperty("minecraft:" + id, value));
        json.add("values", values);
        return json;
    }
}

