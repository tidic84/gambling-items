package dev.gamblingitems.fabric.client;

import dev.gamblingitems.fabric.ModContent;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class GamblingItemsClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        MenuScreens.register(ModContent.UPGRADER_MENU, UpgradeScreen::new);
        BlockEntityRenderers.register(ModContent.STATION_ENTITY, UpgradeStationRenderer::new);
    }
}

