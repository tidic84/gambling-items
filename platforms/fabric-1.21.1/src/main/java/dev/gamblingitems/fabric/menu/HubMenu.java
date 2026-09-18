package dev.gamblingitems.fabric.menu;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.ModContent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/** The terminal home screen: it only forwards to a game, and never holds items. */
public final class HubMenu extends AbstractContainerMenu {
    private final Player owner;

    public HubMenu(int syncId, Inventory inventory) {
        super(ModContent.HUB_MENU, syncId);
        this.owner = inventory.player;
    }

    public static void open(ServerPlayer player) {
        if (player.isSpectator()) return;
        player.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("screen.gamblingitems.hub"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player ignored) {
                return new HubMenu(id, inventory);
            }
        });
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer serverPlayer) || player != owner || !stillValid(player)) return false;
        GameMode[] modes = GameMode.values();
        if (button < 0 || button >= modes.length) return false;
        // Opening a game closes this screen; the terminal is required all along.
        return GameMenus.open(serverPlayer, modes[button], ContainerLevelAccess.NULL);
    }

    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide) return player == owner;
        return player == owner && player.isAlive()
                && player.getInventory().contains(new ItemStack(ModContent.TERMINAL));
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
