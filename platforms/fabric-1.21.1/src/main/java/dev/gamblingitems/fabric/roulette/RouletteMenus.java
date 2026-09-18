package dev.gamblingitems.fabric.roulette;

import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;

public final class RouletteMenus {
    private RouletteMenus() {}

    /**
     * A station hosts its own table. A portable terminal joins a table announced nearby;
     * it never opens a new one on its own.
     */
    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        RouletteGame hosted = access.evaluate((level, pos) ->
                level instanceof ServerLevel serverLevel ? RouletteGames.host(serverLevel, pos) : null, null);
        RouletteGame game = hosted != null ? hosted : RouletteGames.nearest(player);
        if (game == null) {
            player.displayClientMessage(Component.translatable("gui.gamblingitems.no_roulette_table"), true);
            return;
        }
        RouletteGames.join(game, player.getUUID());
        // The round keeps the settings it started with, whatever the configuration says now.
        RouletteSettings settings = game.settings();
        player.openMenu(new ExtendedScreenHandlerFactory<RouletteSettings>() {
            @Override public RouletteSettings getScreenOpeningData(ServerPlayer ignored) { return settings; }
            @Override public Component getDisplayName() {
                return Component.translatable("screen.gamblingitems.roulette");
            }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player ignored) {
                return new RouletteMenu(id, inventory, settings,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.ROULETTE), game);
            }
        });
    }
}
