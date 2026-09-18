package dev.gamblingitems.fabric.tradeup;

import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;

public final class TradeUpMenus {
    private TradeUpMenus() {}

    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        TradeUpSetup setup = ModConfig.tradeUp();
        player.openMenu(new ExtendedScreenHandlerFactory<TradeUpSetup>() {
            @Override public TradeUpSetup getScreenOpeningData(ServerPlayer ignored) { return setup; }
            @Override public Component getDisplayName() { return Component.translatable("screen.gamblingitems.trade_up"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player ignored) {
                return new TradeUpMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.TRADE_UP), access);
            }
        });
    }
}
