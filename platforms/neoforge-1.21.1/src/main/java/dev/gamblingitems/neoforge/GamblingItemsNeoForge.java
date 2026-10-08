package dev.gamblingitems.neoforge;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.battle.BattleLobbies;
import dev.gamblingitems.fabric.bingo.BingoGames;
import dev.gamblingitems.fabric.blackjack.BlackjackTables;
import dev.gamblingitems.fabric.block.StationInteractions;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.crash.CrashGames;
import dev.gamblingitems.fabric.loot.KeyDrops;
import dev.gamblingitems.fabric.menu.GameMenus;
import dev.gamblingitems.fabric.roulette.RouletteGames;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
//#if MC >= 1.20.5
import net.neoforged.neoforge.event.tick.ServerTickEvent;
//#endif
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** NeoForge-specific bootstrap. This class must never import client rendering classes. */
@Mod(GamblingItemsNeoForge.MOD_ID)
public final class GamblingItemsNeoForge {
    public static final String MOD_ID = "gamblingitems";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public GamblingItemsNeoForge(IEventBus modBus) {
        // Every registry is open during the registration events, so the first one registers it all.
        modBus.addListener(RegisterEvent.class, event -> ModContent.initialize());
        //#if MC < 1.20.5
        //$ // A second mod entry point for the client side only exists since NeoForge 20.5.
        //$ if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) dev.gamblingitems.neoforge.client.GamblingItemsNeoForgeClient.register(modBus);
        //#endif
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                ModContent.creativeEntries().forEach(event::accept);
            }
        });

        IEventBus game = NeoForge.EVENT_BUS;
        // Keys are found on mobs, so the cases have a price that is played for, not bought.
        game.addListener(LootTableLoadEvent.class, event ->
                KeyDrops.poolsFor(event.getName())
                        .forEach(pool -> event.getTable().addPool(pool.build())));
        game.addListener(ServerAboutToStartEvent.class, event -> ModConfig.load(event.getServer()));
        // Sent to every player at once only after a /reload succeeded.
        game.addListener(OnDatapackSyncEvent.class, event -> {
            if (event.getPlayer() == null) ModConfig.load(event.getPlayerList().getServer());
        });
        // Shared rounds live on the server, not in a block: a flight survives an unloaded chunk.
        //#if MC >= 1.20.5
        game.addListener(ServerTickEvent.Post.class, event -> {
            MinecraftServer server = event.getServer();
        //#else
        //$ game.addListener(net.neoforged.neoforge.event.TickEvent.ServerTickEvent.class, event -> {
        //$     if (event.phase != net.neoforged.neoforge.event.TickEvent.Phase.END) return;
        //$     MinecraftServer server = event.getServer();
        //#endif
            CrashGames.tick(server);
            RouletteGames.tick(server);
            BlackjackTables.tick(server);
            BattleLobbies.tick(server);
            BingoGames.tick(server);
            StationInteractions.tick(server);
        });
        // An interrupted round is cancelled and every engaged stake is given back exactly once.
        game.addListener(ServerStoppingEvent.class, event -> {
            MinecraftServer server = event.getServer();
            StationInteractions.stop(server);
            CrashGames.stopping(server);
            RouletteGames.stopping(server);
            BlackjackTables.stopping(server);
            BattleLobbies.stopping(server);
            BingoGames.stopping(server);
        });
        game.addListener(ServerStoppedEvent.class, event -> {
            CrashGames.clear();
            RouletteGames.clear();
            BlackjackTables.clear();
            BattleLobbies.clear();
            BingoGames.clear();
        });

        String availableModes = GameMenus.AVAILABLE.stream().map(GameMode::id).collect(Collectors.joining(", "));
        String plannedModes = Arrays.stream(GameMode.values())
                .filter(mode -> !GameMenus.AVAILABLE.contains(mode))
                .map(GameMode::id).collect(Collectors.joining(", "));
        game.addListener(RegisterCommandsEvent.class, event ->
                event.getDispatcher().register(Commands.literal("gamblingitems")
                        .then(Commands.literal("info").executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Gambling Items | NeoForge | Playable: " + availableModes
                                    + " | Use a terminal or a station. Roadmap: " + plannedModes), false);
                            return 1;
                        }))));
        LOGGER.info("Gambling Items loaded. Playable: {}. Planned: {}", availableModes, plannedModes);
        // Release checks start a real server once per Minecraft version; it stops when the world is up.
        if (Boolean.getBoolean("gamblingitems.smoke")) game.addListener(net.neoforged.neoforge.event.server.ServerStartedEvent.class, event -> {
            LOGGER.info("Gambling Items smoke test: server started");
            event.getServer().halt(false);
        });
    }
}
