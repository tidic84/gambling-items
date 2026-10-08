package dev.gamblingitems.neoforge.client;

import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.client.BattleScreen;
import dev.gamblingitems.fabric.client.BingoScreen;
import dev.gamblingitems.fabric.client.BlackjackScreen;
import dev.gamblingitems.fabric.client.CaseScreen;
import dev.gamblingitems.fabric.client.CrashScreen;
import dev.gamblingitems.fabric.client.GameStationRenderer;
import dev.gamblingitems.fabric.client.RouletteScreen;
import dev.gamblingitems.fabric.client.SlotScreen;
import dev.gamblingitems.fabric.client.TradeUpScreen;
import dev.gamblingitems.fabric.client.UpgradeScreen;
import dev.gamblingitems.neoforge.GamblingItemsNeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
//#if MC >= 1.20.5
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
//#endif

//#if MC >= 1.20.5
@Mod(value = GamblingItemsNeoForge.MOD_ID, dist = Dist.CLIENT)
//#endif
public final class GamblingItemsNeoForgeClient {
    //#if MC >= 1.20.5
    public GamblingItemsNeoForgeClient(IEventBus modBus) { register(modBus); }
    //#endif

    public static void register(IEventBus modBus) {
        //#if MC < 1.20.5
        //$ modBus.addListener(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
        //$     net.minecraft.client.gui.screens.MenuScreens.register(ModContent.UPGRADER_MENU, UpgradeScreen::new);
        //$     net.minecraft.client.gui.screens.MenuScreens.register(ModContent.TRADE_UP_MENU, TradeUpScreen::new);
        //$     net.minecraft.client.gui.screens.MenuScreens.register(ModContent.CASE_MENU, CaseScreen::new);
        //$     net.minecraft.client.gui.screens.MenuScreens.register(ModContent.CRASH_MENU, CrashScreen::new);
        //$     net.minecraft.client.gui.screens.MenuScreens.register(ModContent.ROULETTE_MENU, RouletteScreen::new);
        //$     net.minecraft.client.gui.screens.MenuScreens.register(ModContent.BLACKJACK_MENU, BlackjackScreen::new);
        //$     net.minecraft.client.gui.screens.MenuScreens.register(ModContent.BATTLE_MENU, BattleScreen::new);
        //$     net.minecraft.client.gui.screens.MenuScreens.register(ModContent.BINGO_MENU, BingoScreen::new);
        //$     net.minecraft.client.gui.screens.MenuScreens.register(ModContent.SLOT_MENU, SlotScreen::new);
        //$ }));
        //$ modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event ->
        //$         event.registerBlockEntityRenderer(ModContent.STATION_ENTITY, GameStationRenderer::new));
        //$ net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.TickEvent.ClientTickEvent.class, event -> {
        //$     if (event.phase == net.neoforged.neoforge.event.TickEvent.Phase.END)
        //$         dev.gamblingitems.fabric.client.ClientSmoke.tick(net.minecraft.client.Minecraft.getInstance());
        //$ });
        //#else
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                event -> dev.gamblingitems.fabric.client.ClientSmoke.tick(net.minecraft.client.Minecraft.getInstance()));
        //#endif
    }
}
