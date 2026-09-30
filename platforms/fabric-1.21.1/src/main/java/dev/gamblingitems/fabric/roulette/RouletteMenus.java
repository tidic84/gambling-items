package dev.gamblingitems.fabric.roulette;

import dev.gamblingitems.fabric.platform.Platform;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;

public final class RouletteMenus {
    private RouletteMenus() {}

    /** Joins a nearby round, or opens one at the player when no furniture is present. */
    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        RouletteGame hosted = access.evaluate((level, pos) ->
                level instanceof ServerLevel serverLevel ? RouletteGames.host(serverLevel, pos) : null, null);
        RouletteGame game = hosted != null ? hosted : RouletteGames.nearest(player);
        if (game == null) game = RouletteGames.host(player.serverLevel(), player.blockPosition());
        RouletteGame round = game;
        RouletteGames.join(round, player.getUUID());
        // The round keeps the settings it started with, whatever the configuration says now.
        RouletteSetup setup = round.setup();
        Platform.openMenu(player, Component.translatable("screen.gamblingitems.roulette"), setup, RouletteSetup.CODEC,
                (id, inventory, ignored) -> new RouletteMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.ROULETTE), round));
    }
}
