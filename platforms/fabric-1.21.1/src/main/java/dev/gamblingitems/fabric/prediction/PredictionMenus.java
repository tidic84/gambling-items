package dev.gamblingitems.fabric.prediction;

import dev.gamblingitems.fabric.cheat.CasinoSecurity;
import dev.gamblingitems.fabric.menu.Opening;
import dev.gamblingitems.fabric.platform.Platform;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** Opens the prediction market, at an odds board or from a pocket casino. */
public final class PredictionMenus {
    private PredictionMenus() {}

    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator() || CasinoSecurity.refuses(player)) return;
        dev.gamblingitems.fabric.block.StationInteractions.close(player.getUUID());
        PredictionSetup setup = PredictionMarkets.setup();
        Platform.openMenu(player, Component.translatable("screen.gamblingitems.prediction_market"),
                new Opening<>(setup, access == ContainerLevelAccess.NULL), Opening.codec(PredictionSetup.CODEC),
                (id, inventory, ignored) -> new PredictionMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.PREDICTION), access));
    }
}
