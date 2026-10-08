package dev.gamblingitems.fabric.client;

import dev.gamblingitems.fabric.ModContent;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class GamblingItemsClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        MenuScreens.register(ModContent.UPGRADER_MENU, UpgradeScreen::new);
        MenuScreens.register(ModContent.TRADE_UP_MENU, TradeUpScreen::new);
        MenuScreens.register(ModContent.CASE_MENU, CaseScreen::new);
        MenuScreens.register(ModContent.CRASH_MENU, CrashScreen::new);
        MenuScreens.register(ModContent.ROULETTE_MENU, RouletteScreen::new);
        MenuScreens.register(ModContent.BLACKJACK_MENU, BlackjackScreen::new);
        MenuScreens.register(ModContent.BATTLE_MENU, BattleScreen::new);
        MenuScreens.register(ModContent.BINGO_MENU, BingoScreen::new);
        MenuScreens.register(ModContent.SLOT_MENU, SlotScreen::new);
        //#if MC >= 1.21.9
        //$ net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(ModContent.STATION_ENTITY, GameStationRenderer::new);
        //#else
        BlockEntityRenderers.register(ModContent.STATION_ENTITY, GameStationRenderer::new);
        //#endif
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(ClientSmoke::tick);
    }
}
