package dev.gamblingitems.fabric.upgrade;

import dev.gamblingitems.fabric.ModContent;
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
    private UpgradeCatalog catalog() {
        return new UpgradeCatalog(List.of(
                new UpgradeCatalog.Entry(ResourceLocation.withDefaultNamespace("iron_ingot"), 1000),
                new UpgradeCatalog.Entry(ResourceLocation.withDefaultNamespace("diamond"), 10000)), 9000, 9000);
    }
    private ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(ModContent.TERMINAL));
        return player;
    }
    private UpgradeMenu menu(ServerPlayer player, SimpleContainer vault, double draw) {
        return new UpgradeMenu(1, player.getInventory(), catalog(), vault, ContainerLevelAccess.NULL, () -> draw);
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
        vaults.forPlayer(first).setItem(1, new ItemStack(Items.DIAMOND));
        vaults.forPlayer(second).setItem(0, new ItemStack(Items.IRON_INGOT, 7));
        var registries = helper.getLevel().registryAccess();
        PlayerVaults restored = PlayerVaults.load(vaults.save(new CompoundTag(), registries), registries);
        helper.assertTrue(restored.forPlayer(first).getItem(1).is(Items.DIAMOND), "Reward restored");
        helper.assertTrue(restored.forPlayer(second).getItem(0).getCount() == 7, "Input restored");
        helper.assertTrue(restored.forPlayer(second).getItem(1).isEmpty(), "Players do not share rewards");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void destroyedStationCannotAcceptWagers(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.STATION);
        BlockPos pos = helper.absolutePos(relative);
        ServerPlayer player = player(helper);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        SimpleContainer vault = new SimpleContainer(2);
        vault.setItem(0, new ItemStack(Items.IRON_INGOT));
        UpgradeMenu menu = new UpgradeMenu(1, player.getInventory(), catalog(), vault,
                ContainerLevelAccess.create(helper.getLevel(), pos), () -> 0);
        helper.assertTrue(menu.stillValid(player), "Nearby station accessible");
        menu.clickMenuButton(player, 1);
        helper.setBlock(relative, Blocks.AIR);
        helper.assertFalse(menu.clickMenuButton(player, UpgradeMenu.SPIN_BUTTON), "Destroyed station rejects wagers");
        helper.assertTrue(vault.getItem(0).is(Items.IRON_INGOT), "Input retained");
        helper.succeed();
    }
}
