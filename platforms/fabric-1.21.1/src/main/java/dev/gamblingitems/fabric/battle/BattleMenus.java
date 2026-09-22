package dev.gamblingitems.fabric.battle;

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

public final class BattleMenus {
    private BattleMenus() {}

    /**
     * A station hosts its own lobby. A portable terminal joins a lobby announced nearby;
     * it never opens a new one on its own.
     */
    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        BattleLobby hosted = access.evaluate((level, pos) ->
                level instanceof ServerLevel serverLevel ? BattleLobbies.host(serverLevel, pos) : null, null);
        BattleLobby lobby = hosted != null ? hosted : BattleLobbies.nearest(player);
        if (lobby == null) {
            player.displayClientMessage(Component.translatable("gui.gamblingitems.no_battle_lobby"), true);
            return;
        }
        BattleLobbies.join(lobby, player.getUUID());
        BattleSetup setup = lobby.setup();
        player.openMenu(new ExtendedScreenHandlerFactory<BattleSetup>() {
            @Override public BattleSetup getScreenOpeningData(ServerPlayer ignored) { return setup; }
            @Override public Component getDisplayName() {
                return Component.translatable("screen.gamblingitems.case_battle");
            }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player ignored) {
                return new BattleMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.CASE_BATTLE),
                        lobby);
            }
        });
    }
}
