package dev.gamblingitems.fabric.machines;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.menu.Opening;
import dev.gamblingitems.fabric.platform.Platform;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** A prize machine is played alone, at its block: there is no shared round, only the machine in front of you. */
public final class MachineMenus {
    private MachineMenus() {}

    /** False when the machine cannot be opened: away from its block, or closed by the server. */
    public static boolean open(ServerPlayer player, GameMode mode, ContainerLevelAccess access) {
        if (player.isSpectator() || access == ContainerLevelAccess.NULL) return false;
        MachineSetup setup = ModConfig.machine(mode);
        if (!setup.settings().enabled()) {
            dev.gamblingitems.fabric.Compat.message(player, Component.translatable("gui.gamblingitems.machine_disabled",
                    Component.translatable("gui.gamblingitems.mode." + mode.id())), true);
            return false;
        }
        MachineMenu.publishTable(access, setup.settings().table());
        Platform.openMenu(player, Component.translatable("gui.gamblingitems.mode." + mode.id()), new Opening<>(setup, false),
                Opening.codec(MachineSetup.CODEC),
                (id, inventory, ignored) -> new MachineMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.fromId(mode.id())),
                        access));
        return true;
    }
}
