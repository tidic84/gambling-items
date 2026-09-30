package dev.gamblingitems.neoforge.client;

import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.client.BattleScreen;
import dev.gamblingitems.fabric.client.BingoScreen;
import dev.gamblingitems.fabric.client.BlackjackScreen;
import dev.gamblingitems.fabric.client.CaseScreen;
import dev.gamblingitems.fabric.client.CrashScreen;
import dev.gamblingitems.fabric.client.GameStationRenderer;
import dev.gamblingitems.fabric.client.HubScreen;
import dev.gamblingitems.fabric.client.RouletteScreen;
import dev.gamblingitems.fabric.client.SlotScreen;
import dev.gamblingitems.fabric.client.TradeUpScreen;
import dev.gamblingitems.fabric.client.UpgradeScreen;
import dev.gamblingitems.neoforge.GamblingItemsNeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = GamblingItemsNeoForge.MOD_ID, dist = Dist.CLIENT)
public final class GamblingItemsNeoForgeClient {
    public GamblingItemsNeoForgeClient(IEventBus modBus) {
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.register(ModContent.HUB_MENU, HubScreen::new);
            event.register(ModContent.UPGRADER_MENU, UpgradeScreen::new);
            event.register(ModContent.TRADE_UP_MENU, TradeUpScreen::new);
            event.register(ModContent.CASE_MENU, CaseScreen::new);
            event.register(ModContent.CRASH_MENU, CrashScreen::new);
            event.register(ModContent.ROULETTE_MENU, RouletteScreen::new);
            event.register(ModContent.BLACKJACK_MENU, BlackjackScreen::new);
            event.register(ModContent.BATTLE_MENU, BattleScreen::new);
            event.register(ModContent.BINGO_MENU, BingoScreen::new);
            event.register(ModContent.SLOT_MENU, SlotScreen::new);
        });
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event ->
                event.registerBlockEntityRenderer(ModContent.STATION_ENTITY, GameStationRenderer::new));
    }
}
