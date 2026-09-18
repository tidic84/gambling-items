package dev.gamblingitems.fabric.crash;

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

public final class CrashMenus {
    private CrashMenus() {}

    /**
     * A station hosts its own table. A portable terminal joins a table announced nearby;
     * it never opens a new one on its own.
     */
    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        CrashGame hosted = access.evaluate((level, pos) ->
                level instanceof ServerLevel serverLevel ? CrashGames.host(serverLevel, pos) : null, null);
        CrashGame game = hosted != null ? hosted : CrashGames.nearest(player);
        if (game == null) {
            player.displayClientMessage(Component.translatable("gui.gamblingitems.no_crash_table"), true);
            return;
        }
        CrashGames.join(game, player.getUUID());
        // The round keeps the settings it started with, whatever the configuration says now.
        CrashSetup setup = game.setup();
        player.openMenu(new ExtendedScreenHandlerFactory<CrashSetup>() {
            @Override public CrashSetup getScreenOpeningData(ServerPlayer ignored) { return setup; }
            @Override public Component getDisplayName() { return Component.translatable("screen.gamblingitems.crash"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player ignored) {
                return new CrashMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.CRASH), game);
            }
        });
    }
}
