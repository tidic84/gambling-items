package dev.gamblingitems.fabric.crash;

import dev.gamblingitems.fabric.platform.Platform;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;

public final class CrashMenus {
    private CrashMenus() {}

    /** Joins a nearby round, or opens one at the player when no furniture is present. */
    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        CrashGame hosted = access.evaluate((level, pos) ->
                level instanceof ServerLevel serverLevel ? CrashGames.host(serverLevel, pos) : null, null);
        CrashGame game = hosted != null ? hosted : CrashGames.nearest(player);
        if (game == null) game = CrashGames.host(player.serverLevel(), player.blockPosition());
        CrashGame round = game;
        CrashGames.join(round, player.getUUID());
        // The round keeps the settings it started with, whatever the configuration says now.
        CrashSetup setup = round.setup();
        Platform.openMenu(player, Component.translatable("screen.gamblingitems.crash"), setup, CrashSetup.CODEC,
                (id, inventory, ignored) -> new CrashMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.CRASH), round));
    }
}
