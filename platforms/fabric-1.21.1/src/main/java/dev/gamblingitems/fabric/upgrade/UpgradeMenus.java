package dev.gamblingitems.fabric.upgrade;

import dev.gamblingitems.fabric.platform.Platform;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;

public final class UpgradeMenus {
    private UpgradeMenus() {}

    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        UpgradeSetup setup = ModConfig.upgrader();
        Platform.openMenu(player, Component.translatable("screen.gamblingitems.upgrader"), new dev.gamblingitems.fabric.menu.Opening<>(setup, access == ContainerLevelAccess.NULL),
                dev.gamblingitems.fabric.menu.Opening.codec(UpgradeSetup.CODEC),
                (id, inventory, ignored) -> new UpgradeMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.UPGRADER), access));
    }
}
