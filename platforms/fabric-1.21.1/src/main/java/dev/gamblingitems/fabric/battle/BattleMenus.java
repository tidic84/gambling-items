package dev.gamblingitems.fabric.battle;

import dev.gamblingitems.fabric.platform.Platform;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;

public final class BattleMenus {
    private BattleMenus() {}

    /** Joins a nearby round, or opens one at the player when no furniture is present. */
    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        BattleLobby hosted = access.evaluate((level, pos) ->
                level instanceof ServerLevel serverLevel ? BattleLobbies.host(serverLevel, pos) : null, null);
        BattleLobby lobby = hosted != null ? hosted : BattleLobbies.nearest(player);
        if (lobby == null) lobby = BattleLobbies.host(player.serverLevel(), player.blockPosition());
        BattleLobby round = lobby;
        BattleLobbies.join(round, player.getUUID());
        BattleSetup setup = round.setup();
        Platform.openMenu(player, Component.translatable("screen.gamblingitems.case_battle"), setup, BattleSetup.CODEC,
                (id, inventory, ignored) -> new BattleMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.CASE_BATTLE), round));
    }
}
