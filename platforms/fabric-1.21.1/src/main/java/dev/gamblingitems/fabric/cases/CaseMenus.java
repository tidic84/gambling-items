package dev.gamblingitems.fabric.cases;

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

public final class CaseMenus {
    private CaseMenus() {}

    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        CaseSetup setup = ModConfig.cases();
        player.openMenu(new ExtendedScreenHandlerFactory<CaseSetup>() {
            @Override public CaseSetup getScreenOpeningData(ServerPlayer ignored) { return setup; }
            @Override public Component getDisplayName() { return Component.translatable("screen.gamblingitems.cases"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player ignored) {
                return new CaseMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.CASE_OPENING), access);
            }
        });
    }
}
