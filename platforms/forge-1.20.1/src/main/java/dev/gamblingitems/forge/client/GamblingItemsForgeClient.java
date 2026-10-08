package dev.gamblingitems.forge.client;

import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.client.BattleScreen;
import dev.gamblingitems.fabric.client.BingoScreen;
import dev.gamblingitems.fabric.client.BlackjackScreen;
import dev.gamblingitems.fabric.client.CaseScreen;
import dev.gamblingitems.fabric.client.ClientSmoke;
import dev.gamblingitems.fabric.client.CrashScreen;
import dev.gamblingitems.fabric.client.GameStationRenderer;
import dev.gamblingitems.fabric.client.RouletteScreen;
import dev.gamblingitems.fabric.client.SlotScreen;
import dev.gamblingitems.fabric.client.TradeUpScreen;
import dev.gamblingitems.fabric.client.UpgradeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client registrations for Minecraft 1.20.1, called by the mod only on the client side. */
public final class GamblingItemsForgeClient {
    private GamblingItemsForgeClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            MenuScreens.register(ModContent.UPGRADER_MENU, UpgradeScreen::new);
            MenuScreens.register(ModContent.TRADE_UP_MENU, TradeUpScreen::new);
            MenuScreens.register(ModContent.CASE_MENU, CaseScreen::new);
            MenuScreens.register(ModContent.CRASH_MENU, CrashScreen::new);
            MenuScreens.register(ModContent.ROULETTE_MENU, RouletteScreen::new);
            MenuScreens.register(ModContent.BLACKJACK_MENU, BlackjackScreen::new);
            MenuScreens.register(ModContent.BATTLE_MENU, BattleScreen::new);
            MenuScreens.register(ModContent.BINGO_MENU, BingoScreen::new);
            MenuScreens.register(ModContent.SLOT_MENU, SlotScreen::new);
        }));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(ModContent.STATION_ENTITY, GameStationRenderer::new));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) ClientSmoke.tick(Minecraft.getInstance());
        });
    }
}
