package dev.gamblingitems.fabric.loot;

import dev.gamblingitems.core.cases.CaseRarity;
import dev.gamblingitems.fabric.ModContent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Where keys come from: mobs. A tougher mob leaves a rarer key, and only a kill credited to a
 * player counts, so a farm of falling mobs is not a key factory on its own.
 *
 * <p>Chances are deliberately small: a key is the price of a case, and every case is paid from
 * the same catalogue of values as the other games.
 */
public final class KeyDrops {
    /** Mobs anyone meets at night. */
    private static final Set<String> COMMON_MOBS = Set.of(
            "zombie", "skeleton", "spider", "cave_spider", "creeper", "husk", "stray", "drowned",
            "zombie_villager", "silverfish", "slime", "phantom", "pillager", "vindicator", "witch",
            "bogged", "breeze");
    /** Mobs of the nether, harder to farm and better paid. */
    private static final Set<String> NETHER_MOBS = Set.of(
            "blaze", "ghast", "hoglin", "zoglin", "piglin", "magma_cube", "zombified_piglin");
    /** Mobs of the end and their neighbours. */
    private static final Set<String> END_MOBS = Set.of("enderman", "endermite", "shulker", "guardian");
    /** The ones a server remembers killing. */
    private static final Map<String, CaseRarity> CHAMPIONS = Map.of(
            "wither_skeleton", CaseRarity.LEGENDARY,
            "piglin_brute", CaseRarity.LEGENDARY,
            "evoker", CaseRarity.LEGENDARY,
            "ravager", CaseRarity.LEGENDARY,
            "elder_guardian", CaseRarity.MYTHIC,
            "warden", CaseRarity.MYTHIC,
            "wither", CaseRarity.MYTHIC,
            "ender_dragon", CaseRarity.MYTHIC);

    private KeyDrops() {}

    /** One drop: which key, and how often that mob leaves it. */
    private record Drop(CaseRarity rarity, float chance) {}

    public static void initialize() {
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            if (!source.isBuiltin()) return;
            for (Drop drop : dropsFor(key)) {
                builder.withPool(pool(drop));
            }
        });
    }

    /** What a given entity table should also drop. Anything else drops no key at all. */
    static List<Drop> dropsFor(ResourceKey<LootTable> table) {
        ResourceLocation id = table.location();
        if (!id.getNamespace().equals("minecraft") || !id.getPath().startsWith("entities/")) return List.of();
        String mob = id.getPath().substring("entities/".length());
        if (CHAMPIONS.containsKey(mob)) {
            CaseRarity rarity = CHAMPIONS.get(mob);
            return rarity == CaseRarity.MYTHIC
                    ? List.of(new Drop(CaseRarity.MYTHIC, 0.5f), new Drop(CaseRarity.LEGENDARY, 1f))
                    : List.of(new Drop(CaseRarity.LEGENDARY, 0.05f), new Drop(CaseRarity.EPIC, 0.1f));
        }
        if (NETHER_MOBS.contains(mob)) {
            return List.of(new Drop(CaseRarity.RARE, 0.03f), new Drop(CaseRarity.EPIC, 0.006f));
        }
        if (END_MOBS.contains(mob)) {
            return List.of(new Drop(CaseRarity.RARE, 0.04f), new Drop(CaseRarity.EPIC, 0.01f));
        }
        if (COMMON_MOBS.contains(mob)) {
            return List.of(new Drop(CaseRarity.COMMON, 0.04f), new Drop(CaseRarity.UNCOMMON, 0.01f));
        }
        return List.of();
    }

    private static LootPool.Builder pool(Drop drop) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(ModContent.key(drop.rarity())))
                .when(LootItemKilledByPlayerCondition.killedByPlayer())
                .when(LootItemRandomChanceCondition.randomChance(drop.chance()));
    }
}
