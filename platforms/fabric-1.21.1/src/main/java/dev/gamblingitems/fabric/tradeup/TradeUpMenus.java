package dev.gamblingitems.fabric.tradeup;

import dev.gamblingitems.fabric.platform.Platform;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;

public final class TradeUpMenus {
    private TradeUpMenus() {}

    public static void open(ServerPlayer player, ContainerLevelAccess access) {
        if (player.isSpectator()) return;
        TradeUpSetup setup = ModConfig.tradeUp();
        Platform.openMenu(player, Component.translatable("screen.gamblingitems.trade_up"), new dev.gamblingitems.fabric.menu.Opening<>(setup, access == ContainerLevelAccess.NULL),
                dev.gamblingitems.fabric.menu.Opening.codec(TradeUpSetup.CODEC),
                (id, inventory, ignored) -> new TradeUpMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.TRADE_UP), access));
    }
}
