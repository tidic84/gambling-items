package dev.gamblingitems.fabric.upgrade;

import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.value.ValueCatalog;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class UpgradeGameTests implements FabricGameTest {
    private ValueCatalog bookCatalog(GameTestHelper helper) {
        return EnchantedBooks.add(setup().catalog(), com.google.gson.JsonParser.parseString("""
                [{"enchantments":{"minecraft:mending":1},"value":80000},
                 {"enchantments":{"minecraft:sharpness":5},"value":60000},
                 {"enchantments":{"minecraft:sharpness":4},"value":30000},
                 {"enchantments":{"minecraft:mending":1,"minecraft:unbreaking":3},"value":120000}]
                """).getAsJsonArray(), helper.getLevel().registryAccess());
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void enchantedBooksKeepDistinctValuesAndExactComponents(GameTestHelper helper) {
        var catalog = bookCatalog(helper);
        var books = catalog.entries().stream().filter(entry -> entry.stack().is(Items.ENCHANTED_BOOK)).toList();
        helper.assertTrue(books.size() == 4, "Different enchantments and combinations coexist");
        for (var entry : books) {
            helper.assertTrue(catalog.valueOf(entry.stack()) == entry.value(), "Each variant has its own price");
            var renamed = entry.stack();
            renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Special book"));
            helper.assertTrue(catalog.valueOf(renamed) == 0, "Extra components are not silently discarded");
            var copy = entry.stack();
            copy.set(DataComponents.CUSTOM_NAME, Component.literal("Mutated copy"));
            helper.assertTrue(catalog.valueOf(entry.stack()) == entry.value(), "Returned stacks cannot mutate the catalogue");
        }
        helper.assertTrue(catalog.valueOf(new ItemStack(Items.ENCHANTED_BOOK)) == 0, "An empty book is not an enchanted target");
        var owner = player(helper);
        var vault = new SimpleContainer(2);
        var stake = books.getFirst();
        vault.setItem(0, stake.stack());
        var menu = new UpgradeMenu(1, owner.getInventory(), new UpgradeSetup(catalog, 9000, 9000),
                vault, ContainerLevelAccess.NULL, () -> 0);
        helper.assertTrue(menu.getSlot(0).mayPlace(stake.stack()), "Configured book accepted as stake");
        menu.clickMenuButton(owner, catalog.entries().size() - 1);
        helper.assertTrue(Math.abs(menu.chance() - 0.9 * stake.value() / 120000) < 1e-12,
                "Book stake uses its exact variant price");
        helper.assertTrue(menu.clickMenuButton(owner, UpgradeMenu.SPIN_BUTTON), "Book can upgrade to a more valuable book");
        helper.assertTrue(vault.getItem(0).isEmpty(), "Book stake consumed");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void enchantedBookSetupSurvivesNetworkAndPaysTheSelectedBook(GameTestHelper helper) {
        var setup = new UpgradeSetup(bookCatalog(helper), 9000, 9000);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                helper.getLevel().registryAccess());
        try {
            UpgradeSetup.CODEC.encode(buffer, setup);
            var decoded = UpgradeSetup.CODEC.decode(buffer);
            helper.assertTrue(decoded.catalog().entries().equals(setup.catalog().entries()),
                    "All book enchantments and prices reach the client");
            var owner = player(helper);
            var vault = new SimpleContainer(2);
            vault.setItem(0, new ItemStack(Items.IRON_INGOT));
            var menu = new UpgradeMenu(1, owner.getInventory(), setup, vault, ContainerLevelAccess.NULL, () -> 0);
            int target = setup.catalog().entries().size() - 1;
            menu.clickMenuButton(owner, target);
            var expected = decoded.catalog().entries().get(target).stack();
            helper.assertTrue(menu.clickMenuButton(owner, UpgradeMenu.SPIN_BUTTON), "Book target can be won");
            helper.assertTrue(ItemStack.isSameItemSameComponents(vault.getItem(1), expected), "Reward keeps every enchantment");
            helper.assertTrue(vault.getItem(0).isEmpty(), "Stake is consumed once");
            helper.assertFalse(menu.clickMenuButton(owner, UpgradeMenu.SPIN_BUTTON), "Duplicate wager rejected");
            var vaults = new PlayerVaults();
            vaults.forPlayer(owner.getUUID(), VaultSection.UPGRADER).setItem(1, vault.getItem(1));
            var registries = helper.getLevel().registryAccess();
            var restored = PlayerVaults.load(vaults.save(new CompoundTag(), registries), registries);
            helper.assertTrue(ItemStack.isSameItemSameComponents(expected,
                    restored.forPlayer(owner.getUUID(), VaultSection.UPGRADER).getItem(1)), "Saved rewards retain enchantments");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void defaultBooksAreAvailableOnlyInTheUpgrader(GameTestHelper helper) {
        var config = dev.gamblingitems.fabric.config.ModConfig.upgrader().catalog();
        helper.assertTrue(config.entries().stream().anyMatch(entry -> entry.stack().is(Items.ENCHANTED_BOOK)
                && !entry.displayName().getString().equals(entry.stack().getHoverName().getString())),
                "Configured books have descriptive names");
        for (var entry : config.entries()) {
            if (!entry.stack().is(Items.ENCHANTED_BOOK)) continue;
            if (config.valueOf(entry.stack()) <= 0) continue;
            if (ItemStack.isSameItemSameComponents(entry.stack(), new ItemStack(Items.ENCHANTED_BOOK))) continue;
            helper.assertTrue(dev.gamblingitems.fabric.config.ModConfig.values().valueOf(entry.stack()) == 0,
                    "Book variants do not change other games' economy");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void largeCatalogSelectionCannotCollideWithSpin(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        var entries = new java.util.ArrayList<ValueCatalog.Entry>();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (item == Items.AIR || item == Items.IRON_INGOT) continue;
            entries.add(new ValueCatalog.Entry(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item), 10000));
            if (entries.size() == 1100) break;
        }
        entries.add(new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("iron_ingot"), 1000));
        var setup = new UpgradeSetup(new ValueCatalog(entries), 9000, 9000);
        var vault = new SimpleContainer(2);
        vault.setItem(0, new ItemStack(Items.IRON_INGOT));
        var menu = new UpgradeMenu(1, player.getInventory(), setup, vault, ContainerLevelAccess.NULL, () -> 0);
        helper.assertTrue(menu.clickMenuButton(player, 1000), "Entry 1000 is selectable");
        helper.assertTrue(menu.selectedIndex() == 1000 && !menu.isAnimating(), "Selection never starts a wager");
        helper.assertTrue(!vault.getItem(0).isEmpty(), "Selection preserves the stake");
        helper.assertTrue(menu.clickMenuButton(player, UpgradeMenu.SPIN_BUTTON), "Separate spin action works");
        helper.assertTrue(menu.isAnimating() && vault.getItem(0).isEmpty(), "Exactly one wager is made");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aTinyChanceSurvivesTheAnimationSnapshot(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        var setup = new UpgradeSetup(new ValueCatalog(List.of(
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("iron_ingot"), 1),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("diamond"), ValueCatalog.MAX_VALUE))), 9000, 9000);
        var vault = new SimpleContainer(2);
        vault.setItem(0, new ItemStack(Items.IRON_INGOT));
        var menu = new UpgradeMenu(1, player.getInventory(), setup, vault, ContainerLevelAccess.NULL, () -> 0);
        menu.clickMenuButton(player, 1);
        double announced = menu.chance();
        helper.assertTrue(menu.clickMenuButton(player, UpgradeMenu.SPIN_BUTTON), "Tiny but positive chance is playable");
        helper.assertTrue(menu.chance() > 0 && Math.abs(menu.chance() - announced) < 1e-15,
                "Animation does not round a positive chance down to zero");
        helper.succeed();
    }

    private UpgradeSetup setup() {
        return new UpgradeSetup(new ValueCatalog(List.of(
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("iron_ingot"), 1000),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("diamond"), 10000))), 9000, 9000);
    }
    private ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(ModContent.TERMINAL));
        return player;
    }
    private UpgradeMenu menu(ServerPlayer player, SimpleContainer vault, double draw) {
        return new UpgradeMenu(1, player.getInventory(), setup(), vault, ContainerLevelAccess.NULL, () -> draw);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void winConsumesEntireStakeAndRejectsDuplicate(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SimpleContainer vault = new SimpleContainer(2);
        vault.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
        UpgradeMenu menu = menu(player, vault, 0);
        helper.assertTrue(menu.clickMenuButton(player, 1), "Select target");
        helper.assertTrue(menu.clickMenuButton(player, UpgradeMenu.SPIN_BUTTON), "First wager accepted");
        helper.assertTrue(vault.getItem(0).isEmpty(), "Whole stake consumed");
        helper.assertTrue(vault.getItem(1).is(Items.DIAMOND) && vault.getItem(1).getCount() == 1, "One reward");
        helper.assertFalse(menu.clickMenuButton(player, UpgradeMenu.SPIN_BUTTON), "Duplicate rejected");
        helper.assertTrue(menu.quickMoveStack(player, 1).isEmpty(), "Reward locked during animation");
        menu.removed(player);
        helper.assertTrue(vault.getItem(1).is(Items.DIAMOND), "Closing preserves reward");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void lossAndReopeningCannotReroll(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SimpleContainer vault = new SimpleContainer(2);
        vault.setItem(0, new ItemStack(Items.IRON_INGOT));
        UpgradeMenu first = menu(player, vault, 0.99);
        first.clickMenuButton(player, 1);
        helper.assertTrue(first.clickMenuButton(player, UpgradeMenu.SPIN_BUTTON), "Wager accepted");
        first.removed(player);
        helper.assertTrue(vault.isEmpty(), "Losing stake not refunded");
        UpgradeMenu reopened = menu(player, vault, 0);
        vault.setItem(0, new ItemStack(Items.IRON_INGOT));
        reopened.clickMenuButton(player, 1);
        helper.assertFalse(reopened.clickMenuButton(player, UpgradeMenu.SPIN_BUTTON), "Cooldown survives menu reopening");
        helper.assertTrue(vault.getItem(0).getCount() == 1, "Rejected attempt preserves stake");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void invalidTargetsAndForeignPlayersCannotSpend(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ServerPlayer other = player(helper);
        SimpleContainer vault = new SimpleContainer(2);
        vault.setItem(0, new ItemStack(Items.DIAMOND));
        UpgradeMenu menu = menu(owner, vault, 0);
        helper.assertFalse(menu.clickMenuButton(other, 1), "Foreign selection rejected");
        helper.assertFalse(menu.clickMenuButton(owner, 999), "Unknown target rejected");
        menu.clickMenuButton(owner, 0);
        helper.assertFalse(menu.clickMenuButton(owner, UpgradeMenu.SPIN_BUTTON), "Downgrade rejected");
        menu.clickMenuButton(owner, 1);
        helper.assertFalse(menu.clickMenuButton(owner, UpgradeMenu.SPIN_BUTTON), "Same value rejected");
        helper.assertTrue(vault.getItem(0).is(Items.DIAMOND), "No rejected request consumes input");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void modifiedAndUnlistedInputsAreRejected(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        UpgradeMenu menu = menu(player, new SimpleContainer(2), 0);
        ItemStack renamed = new ItemStack(Items.DIAMOND);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Special diamond"));
        helper.assertFalse(menu.getSlot(0).mayPlace(renamed), "Do not strip custom data");
        helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)), "Unlisted item rejected");
        helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_INGOT)), "Listed plain item accepted");
        helper.assertFalse(menu.getSlot(1).mayPlace(new ItemStack(Items.DIAMOND)), "Cannot insert into reward slot");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fullInventoryKeepsInputAndRewardsInVault(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        SimpleContainer vault = new SimpleContainer(2);
        vault.setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        vault.setItem(1, new ItemStack(Items.DIAMOND));
        menu(player, vault, 0).removed(player);
        helper.assertTrue(vault.getItem(0).getCount() == 4, "Overflow stays recoverable");
        helper.assertTrue(vault.getItem(1).is(Items.DIAMOND), "Reward stays recoverable");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void vaultRoundTripKeepsPlayersSeparate(GameTestHelper helper) {
        PlayerVaults vaults = new PlayerVaults();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        vaults.forPlayer(first, VaultSection.UPGRADER).setItem(1, new ItemStack(Items.DIAMOND));
        vaults.forPlayer(second, VaultSection.UPGRADER).setItem(0, new ItemStack(Items.IRON_INGOT, 7));
        vaults.forPlayer(first, VaultSection.TRADE_UP).setItem(2, new ItemStack(Items.EMERALD, 3));
        var registries = helper.getLevel().registryAccess();
        PlayerVaults restored = PlayerVaults.load(vaults.save(new CompoundTag(), registries), registries);
        helper.assertTrue(restored.forPlayer(first, VaultSection.UPGRADER).getItem(1).is(Items.DIAMOND), "Reward restored");
        helper.assertTrue(restored.forPlayer(second, VaultSection.UPGRADER).getItem(0).getCount() == 7, "Input restored");
        helper.assertTrue(restored.forPlayer(second, VaultSection.UPGRADER).getItem(1).isEmpty(),
                "Players do not share rewards");
        helper.assertTrue(restored.forPlayer(first, VaultSection.TRADE_UP).getItem(2).getCount() == 3,
                "Each game keeps its own slots");
        helper.assertTrue(restored.forPlayer(first, VaultSection.UPGRADER).getItem(0).isEmpty(),
                "Games do not share slots");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void destroyedStationCannotAcceptWagers(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.UPGRADE_STATION);
        BlockPos pos = helper.absolutePos(relative);
        ServerPlayer player = player(helper);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        SimpleContainer vault = new SimpleContainer(2);
        vault.setItem(0, new ItemStack(Items.IRON_INGOT));
        UpgradeMenu menu = new UpgradeMenu(1, player.getInventory(), setup(), vault,
                ContainerLevelAccess.create(helper.getLevel(), pos), () -> 0);
        helper.assertTrue(menu.stillValid(player), "Nearby station accessible");
        menu.clickMenuButton(player, 1);
        helper.setBlock(relative, Blocks.AIR);
        helper.assertFalse(menu.clickMenuButton(player, UpgradeMenu.SPIN_BUTTON), "Destroyed station rejects wagers");
        helper.assertTrue(vault.getItem(0).is(Items.IRON_INGOT), "Input retained");
        helper.succeed();
    }
}
