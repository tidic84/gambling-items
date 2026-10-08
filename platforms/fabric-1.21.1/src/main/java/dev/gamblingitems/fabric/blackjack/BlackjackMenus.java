package dev.gamblingitems.fabric.blackjack;

import dev.gamblingitems.fabric.platform.Platform;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;

public final class BlackjackMenus {
    private BlackjackMenus() {}

    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        BlackjackTable table = BlackjackTables.of(player.server, player.getUUID());
        // The hand keeps the settings it was dealt with, whatever the configuration says now.
        BlackjackSetup setup = table.setup();
        Platform.openMenu(player, Component.translatable("screen.gamblingitems.blackjack"), new dev.gamblingitems.fabric.menu.Opening<>(setup, access == ContainerLevelAccess.NULL),
                dev.gamblingitems.fabric.menu.Opening.codec(BlackjackSetup.CODEC),
                (id, inventory, ignored) -> new BlackjackMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.BLACKJACK),
                        table, access));
    }
}
