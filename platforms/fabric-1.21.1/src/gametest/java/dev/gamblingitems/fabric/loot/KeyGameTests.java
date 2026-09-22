package dev.gamblingitems.fabric.loot;

import dev.gamblingitems.core.cases.CaseRarity;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.cases.CaseDefinition;
import dev.gamblingitems.fabric.cases.CaseMenu;
import dev.gamblingitems.fabric.config.ModConfig;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;

public class KeyGameTests implements FabricGameTest {
    private static ResourceKey<LootTable> table(String mob) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                ResourceLocation.withDefaultNamespace("entities/" + mob));
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyRarityHasItsOwnKeyAndItsOwnCase(GameTestHelper helper) {
        for (CaseRarity rarity : CaseRarity.values()) {
            var key = ModContent.key(rarity);
            helper.assertTrue(key != null, "A key exists for " + rarity.id());
            helper.assertTrue(BuiltInRegistries.ITEM.getKey(key).getPath().equals(rarity.keyPath()),
                    "The key is registered under its rarity");
            CaseDefinition definition = null;
            for (CaseDefinition candidate : ModConfig.cases().cases().cases()) {
                if (candidate.id().equals(rarity.caseId())) definition = candidate;
            }
            helper.assertTrue(definition != null, "A case exists for " + rarity.id());
            helper.assertTrue(definition.priceStack().is(key), "That case is opened by that key only");
            helper.assertTrue(definition.priceCount() == 1, "One key, one opening");
            helper.assertTrue(definition.priceValue(ModConfig.values()) == 0,
                    "A key is found, never priced in the catalogue");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aKeyOpensItsOwnCaseAndNoOther(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(ModContent.TERMINAL));
        SimpleContainer vault = new SimpleContainer(CaseMenu.REWARD_SLOT + 1);
        vault.setItem(CaseMenu.PRICE_SLOT, new ItemStack(ModContent.key(CaseRarity.RARE)));
        CaseMenu menu = new CaseMenu(1, player.getInventory(), ModConfig.cases(), vault,
                ContainerLevelAccess.NULL);
        var cases = ModConfig.cases().cases().cases();
        int rare = -1, epic = -1;
        for (int index = 0; index < cases.size(); index++) {
            if (cases.get(index).id().equals(CaseRarity.RARE.caseId())) rare = index;
            if (cases.get(index).id().equals(CaseRarity.EPIC.caseId())) epic = index;
        }
        helper.assertTrue(menu.clickMenuButton(player, epic), "Selecting the epic case is allowed");
        helper.assertFalse(menu.canOpen(), "A rare key does not open the epic case");
        helper.assertTrue(menu.clickMenuButton(player, rare), "Selecting the rare case is allowed");
        helper.assertTrue(menu.canOpen(), "The rare key opens the rare case");
        helper.assertTrue(menu.clickMenuButton(player, CaseMenu.OPEN_BUTTON), "The opening is accepted");
        helper.assertTrue(vault.getItem(CaseMenu.PRICE_SLOT).isEmpty(), "The key is consumed once");
        helper.assertFalse(vault.getItem(CaseMenu.REWARD_SLOT).isEmpty(), "A reward is always given");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void keysAreNotAStakeAnywhere(GameTestHelper helper) {
        for (CaseRarity rarity : CaseRarity.values()) {
            helper.assertTrue(ModConfig.values().valueOf(new ItemStack(ModContent.key(rarity))) == 0,
                    "A key can never be wagered as value");
        }
        helper.assertTrue(ModConfig.values().valueOf(new ItemStack(Items.IRON_INGOT)) > 0,
                "An ordinary item still is");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tougherMobsCarryRarerKeys(GameTestHelper helper) {
        helper.assertTrue(KeyDrops.dropsFor(table("zombie")).size() == 2, "A common mob drops two kinds");
        helper.assertTrue(KeyDrops.dropsFor(table("wither_skeleton")).size() == 2, "So does a champion");
        helper.assertTrue(KeyDrops.dropsFor(table("cow")).isEmpty(), "A cow carries no key");
        helper.assertTrue(KeyDrops.dropsFor(ResourceKey.create(
                net.minecraft.core.registries.Registries.LOOT_TABLE,
                ResourceLocation.withDefaultNamespace("chests/simple_dungeon"))).isEmpty(),
                "Chests are not a source of keys");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aKilledMobCanLeaveAKey(GameTestHelper helper) {
        // The chance is small by design, so this drives the table itself rather than a real kill.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LivingEntity zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        var table = helper.getLevel().getServer().reloadableRegistries()
                .getLootTable(zombie.getLootTable());
        var parameters = new net.minecraft.world.level.storage.loot.LootParams.Builder(helper.getLevel())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, zombie)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                        zombie.position())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE,
                        helper.getLevel().damageSources().playerAttack(player))
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ATTACKING_ENTITY,
                        player)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.LAST_DAMAGE_PLAYER,
                        player)
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
        boolean keyPossible = false;
        for (int attempt = 0; attempt < 400 && !keyPossible; attempt++) {
            List<ItemStack> drops = table.getRandomItems(parameters);
            for (ItemStack stack : drops) {
                if (stack.getItem() == ModContent.key(CaseRarity.COMMON)
                        || stack.getItem() == ModContent.key(CaseRarity.UNCOMMON)) {
                    keyPossible = true;
                }
            }
        }
        helper.assertTrue(keyPossible, "A zombie killed by a player eventually leaves a key");
        zombie.discard();
        helper.succeed();
    }
}
