package dev.gamblingitems.fabric.slots;

import dev.gamblingitems.fabric.platform.Platform;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** A cabinet is played alone: there is no shared round to join, only the machine in front of you. */
public final class SlotMenus {
    private SlotMenus() {}

    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        SlotSetup setup = ModConfig.slots();
        Platform.openMenu(player, Component.translatable("screen.gamblingitems.slot_machine"), setup, SlotSetup.CODEC,
                (id, inventory, ignored) -> new SlotMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.SLOT_MACHINE),
                        access));
    }
}
