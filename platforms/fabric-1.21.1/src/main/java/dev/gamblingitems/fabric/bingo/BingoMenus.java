package dev.gamblingitems.fabric.bingo;

import dev.gamblingitems.fabric.platform.Platform;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        Platform.openMenu(player, Component.translatable("screen.gamblingitems.bingo"), setup, BingoSetup.CODEC,
                (id, inventory, ignored) -> new BingoMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.BINGO), round));
    }
}
