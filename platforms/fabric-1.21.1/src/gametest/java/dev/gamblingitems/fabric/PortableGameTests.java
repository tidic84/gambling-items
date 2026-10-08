package dev.gamblingitems.fabric;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.item.TerminalItem;
import dev.gamblingitems.fabric.menu.GameMenus;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PortableGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void theTerminalOpensItsItemGamesWithoutABlock(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(12000, 100, 12000);
        player.getInventory().setItem(0, new ItemStack(ModContent.TERMINAL));
        for (GameMode mode : GameMenus.TERMINAL) {
            helper.assertTrue(GameMenus.open(player, mode, ContainerLevelAccess.NULL), mode + " opens from the terminal");
            helper.assertTrue(player.containerMenu.stillValid(player), mode + " stays open without a station");
            var id = net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(player.containerMenu.getType());
            helper.assertTrue(id.equals(ModContent.id(mode.id())), "The terminal opens " + mode);
            player.closeContainer();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void thePocketCasinoOpensCasinoGamesWithoutABlock(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(12000, 100, 12000);
        player.getInventory().setItem(0, new ItemStack(ModContent.POCKET_CASINO));
        for (GameMode mode : GameMenus.CASINO) {
            helper.assertTrue(GameMenus.open(player, mode, ContainerLevelAccess.NULL), mode + " opens from the pocket casino");
            helper.assertTrue(player.containerMenu.stillValid(player), mode + " stays open without a block");
            var id = net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(player.containerMenu.getType());
            helper.assertTrue(id.equals(ModContent.id(mode.id())), "The pocket casino opens " + mode);
            player.closeContainer();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void eachPortableItemReachesItsOwnGamesOnly(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModContent.TERMINAL));
        for (GameMode mode : GameMode.values())
            helper.assertTrue(TerminalItem.hasAccess(player, mode) == GameMenus.TERMINAL.contains(mode),
                    "The exchange terminal reaches " + mode + " only if it is an item game");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModContent.POCKET_CASINO));
        for (GameMode mode : GameMode.values())
            helper.assertTrue(TerminalItem.hasAccess(player, mode) == GameMenus.CASINO.contains(mode),
                    "The pocket casino reaches " + mode + " only if it is a casino game");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        helper.assertFalse(TerminalItem.hasAccess(player, GameMode.CRASH), "Removing the item removes access");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void recipesSupplyVanillaAndModdedTargets(GameTestHelper helper) {
        var catalog = ModConfig.values();
        helper.assertTrue(catalog.entries().size() > 512, "The catalogue is no longer limited to 512 entries");
        helper.assertTrue(catalog.valueOf(new ItemStack(Items.OAK_PLANKS)) > 0, "Recipe output is in the catalogue");
        helper.assertTrue(catalog.valueOf(new ItemStack(Items.CRAFTING_TABLE)) > 0, "Chained crafting recipe is priced");
        helper.assertTrue(catalog.valueOf(new ItemStack(Items.NETHERITE_AXE)) >
                catalog.valueOf(new ItemStack(Items.NETHERITE_INGOT)), "Smithing includes its base equipment and template");
        helper.assertTrue(catalog.valueOf(new ItemStack(Items.DECORATED_POT)) > 0, "Special pot recipe has a plain target");
        helper.assertTrue(catalog.valueOf(new ItemStack(ModContent.TERMINAL)) > 0,
                "A recipe in a mod namespace is automatically valued");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aLargeCatalogSurvivesNetworkEncoding(GameTestHelper helper) {
        var catalog = ModConfig.values();
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                helper.getLevel().registryAccess());
        try {
            dev.gamblingitems.fabric.value.ValueCatalog.CODEC.encode(buffer, catalog);
            helper.assertTrue(buffer.readableBytes() < 1_000_000, "Opening data stays below the payload limit");
            var decoded = dev.gamblingitems.fabric.value.ValueCatalog.CODEC.decode(buffer);
            helper.assertTrue(decoded.entries().equals(catalog.entries()), "Every identifier and value arrives intact");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void explicitModValuesOverrideRecipesAndExclusionsRemoveTargets(GameTestHelper helper) {
        var id = ModContent.id("terminal");
        var configured = new dev.gamblingitems.fabric.value.ValueCatalog(java.util.List.of(
                new dev.gamblingitems.fabric.value.ValueCatalog.Entry(id, 54321)));
        var options = com.google.gson.JsonParser.parseString("""
                {"enabled":true,"fallbackValue":700,"namespaceFallbackValues":{"minecraft":100},
                 "excludedItems":["minecraft:diamond_block"]}
                """).getAsJsonObject();
        var result = dev.gamblingitems.fabric.value.RecipeValuation.build(helper.getLevel().getServer(), configured, options);
        helper.assertTrue(result.catalog().valueOf(new ItemStack(ModContent.TERMINAL)) == 54321,
                "Explicit price wins over a modded recipe");
        helper.assertTrue(result.catalog().valueOf(new ItemStack(Items.DIAMOND_BLOCK)) == 0, "Excluded target stays absent");
        helper.assertTrue(result.report().getAsJsonObject("items").getAsJsonObject(id.toString())
                .get("source").getAsString().equals("configured"), "Provenance is exported");
        helper.succeed();
    }
}
