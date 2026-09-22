package dev.gamblingitems.fabric.bingo;

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

public final class BingoMenus {
    private BingoMenus() {}

    /**
     * A station or a table hosts its own round. A portable terminal joins one announced nearby,
     * and opens one where its owner stands when there is none: a drum needs no furniture, and a
     * player with a terminal should never be left without a game to sit at.
     */
    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        BingoGame hosted = access.evaluate((level, pos) ->
                level instanceof ServerLevel serverLevel ? BingoGames.host(serverLevel, pos) : null, null);
        BingoGame game = hosted != null ? hosted : BingoGames.nearest(player);
        if (game == null && player.level() instanceof ServerLevel level) {
            game = BingoGames.host(level, player.blockPosition());
        }
        if (game == null) {
            player.displayClientMessage(Component.translatable("gui.gamblingitems.no_bingo_table"), true);
            return;
        }
        BingoGame round = game;
        BingoGames.join(round, player.getUUID());
        BingoSetup setup = round.setup();
        player.openMenu(new ExtendedScreenHandlerFactory<BingoSetup>() {
            @Override public BingoSetup getScreenOpeningData(ServerPlayer ignored) { return setup; }
            @Override public Component getDisplayName() { return Component.translatable("screen.gamblingitems.bingo"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player ignored) {
                return new BingoMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.BINGO), round);
            }
        });
    }
}
