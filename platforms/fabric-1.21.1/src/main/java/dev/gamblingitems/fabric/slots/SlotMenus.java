package dev.gamblingitems.fabric.slots;

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

/** A cabinet is played alone: there is no shared round to join, only the machine in front of you. */
public final class SlotMenus {
    private SlotMenus() {}

    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        SlotSetup setup = ModConfig.slots();
        player.openMenu(new ExtendedScreenHandlerFactory<SlotSetup>() {
            @Override public SlotSetup getScreenOpeningData(ServerPlayer ignored) { return setup; }
            @Override public Component getDisplayName() {
                return Component.translatable("screen.gamblingitems.slot_machine");
            }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player ignored) {
                return new SlotMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.SLOT_MACHINE),
                        access);
            }
        });
    }
}
