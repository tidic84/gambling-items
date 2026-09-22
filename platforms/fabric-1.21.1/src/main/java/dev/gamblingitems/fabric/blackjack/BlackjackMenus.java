package dev.gamblingitems.fabric.blackjack;

import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;

public final class BlackjackMenus {
    private BlackjackMenus() {}

    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        BlackjackTable table = BlackjackTables.of(player.server, player.getUUID());
        // The hand keeps the settings it was dealt with, whatever the configuration says now.
        BlackjackSetup setup = table.setup();
        player.openMenu(new ExtendedScreenHandlerFactory<BlackjackSetup>() {
            @Override public BlackjackSetup getScreenOpeningData(ServerPlayer ignored) { return setup; }
            @Override public Component getDisplayName() {
                return Component.translatable("screen.gamblingitems.blackjack");
            }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player ignored) {
                return new BlackjackMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.BLACKJACK),
                        table, access);
            }
        });
    }
}
