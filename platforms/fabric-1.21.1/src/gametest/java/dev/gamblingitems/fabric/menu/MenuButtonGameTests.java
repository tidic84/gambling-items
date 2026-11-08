package dev.gamblingitems.fabric.menu;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.ModContent;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** Exercises the packet handler with real players and each of the nine real menus. */
public class MenuButtonGameTests implements FabricGameTest {
    // The prize machines are played on their cabinet, never from a portable menu.
    private static final java.util.List<GameMode> GAMES = java.util.stream.Stream.concat(
            GameMenus.TERMINAL.stream(), GameMenus.CASINO.stream()).toList();
    private static ServerPlayer player(GameTestHelper helper) {
        // Vanilla's mock hardcodes isSpectator() to false even after setGameMode.
        // Use a real ServerPlayer with the helper's embedded test connection.
        ServerPlayer connectionOwner = helper.makeMockServerPlayerInLevel();
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "menu-packet-test"),
                ClientInformation.createDefault());
        player.connection = connectionOwner.connection;
        player.getInventory().setItem(0, new ItemStack(ModContent.TERMINAL));
        player.getInventory().setItem(1, new ItemStack(ModContent.POCKET_CASINO));
        return player;
    }

    private static boolean send(ServerPlayer player, int containerId, int button) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new MenuButton(containerId, button).write(buffer);
            return MenuButton.handle(player, MenuButton.read(buffer));
        } finally {
            buffer.release();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyGameCanSwitchTabsThroughThePacket(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        for (GameMode source : GAMES) {
            for (GameMode target : GameMenus.family(source)) {
                helper.assertTrue(GameMenus.open(player, source, ContainerLevelAccess.NULL), source + " opens");
                helper.assertTrue(send(player, player.containerMenu.containerId, TerminalTabs.button(target)),
                        source + " can switch to " + target);
                helper.assertTrue(BuiltInRegistries.MENU.getKey(player.containerMenu.getType())
                        .equals(ModContent.id(target.id())), "The target menu is open");
                player.closeContainer();
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyGameRejectsStaleInvalidAndSpectatorPackets(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        for (GameMode mode : GAMES) {
            GameMenus.open(player, mode, ContainerLevelAccess.NULL);
            int container = player.containerMenu.containerId;
            int tab = TerminalTabs.button(mode);
            helper.assertFalse(send(player, container + 1, tab), mode + " rejects a stale container");
            helper.assertFalse(send(player, container, -1), mode + " rejects negative actions");
            helper.assertFalse(send(player, container, Integer.MAX_VALUE), mode + " rejects unknown actions");
            player.setGameMode(GameType.SPECTATOR);
            helper.assertFalse(send(player, container, tab), mode + " rejects spectators");
            player.setGameMode(GameType.SURVIVAL);
            player.closeContainer();
            helper.assertFalse(send(player, container, tab), mode + " rejects a closed menu");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyGameRejectsLostAccessAndForeignPlayers(GameTestHelper helper) {
        ServerPlayer owner = player(helper), other = player(helper);
        for (GameMode mode : GAMES) {
            GameMenus.open(owner, mode, ContainerLevelAccess.NULL);
            var menu = owner.containerMenu;
            int tab = TerminalTabs.button(mode);
            helper.assertFalse(menu.clickMenuButton(other, tab), mode + " rejects a foreign player's tab");
            owner.getInventory().setItem(0, ItemStack.EMPTY);
            owner.getInventory().setItem(1, ItemStack.EMPTY);
            helper.assertFalse(send(owner, menu.containerId, tab), mode + " rejects lost portable access");
            owner.getInventory().setItem(0, new ItemStack(ModContent.TERMINAL));
            owner.getInventory().setItem(1, new ItemStack(ModContent.POCKET_CASINO));
            owner.closeContainer();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void inventoryMenusCannotReceiveGamePackets(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        helper.assertFalse(send(player, player.containerMenu.containerId, TerminalTabs.button(GameMode.UPGRADER)),
                "A packet cannot open a game from an unrelated menu");
        helper.succeed();
    }
}
