package dev.gamblingitems.fabric.cases;

import dev.gamblingitems.fabric.platform.Platform;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;

public final class CaseMenus {
    private CaseMenus() {}

    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        CaseSetup setup = ModConfig.cases();
        Platform.openMenu(player, Component.translatable("screen.gamblingitems.cases"), setup, CaseSetup.CODEC,
                (id, inventory, ignored) -> new CaseMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.CASE_OPENING),
                        access));
    }
}
