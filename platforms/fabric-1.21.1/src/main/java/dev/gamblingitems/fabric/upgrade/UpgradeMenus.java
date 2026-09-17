package dev.gamblingitems.fabric.upgrade;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;

public final class UpgradeMenus {
    private UpgradeMenus() {}
    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        UpgradeCatalog catalog = UpgradeConfig.current();
        player.openMenu(new ExtendedScreenHandlerFactory<UpgradeCatalog>() {
            @Override public UpgradeCatalog getScreenOpeningData(ServerPlayer ignored) { return catalog; }
            @Override public Component getDisplayName() { return Component.translatable("screen.gamblingitems.upgrader"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player ignored) {
                return new UpgradeMenu(id, inventory, catalog,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID()), access);
            }
        });
    }
}

