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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                ModContent.creativeEntries().forEach(event::accept);
            }
        });

        IEventBus game = NeoForge.EVENT_BUS;
        // Keys are found on mobs, so the cases have a price that is played for, not bought.
        game.addListener(LootTableLoadEvent.class, event ->
                KeyDrops.poolsFor(ResourceKey.create(Registries.LOOT_TABLE, event.getName()))
                        .forEach(pool -> event.getTable().addPool(pool.build())));
        game.addListener(ServerAboutToStartEvent.class, event -> ModConfig.load(event.getServer()));
        // Sent to every player at once only after a /reload succeeded.
        game.addListener(OnDatapackSyncEvent.class, event -> {
            if (event.getPlayer() == null) ModConfig.load(event.getPlayerList().getServer());
        });
        // Shared rounds live on the server, not in a block: a flight survives an unloaded chunk.
        game.addListener(ServerTickEvent.Post.class, event -> {
            MinecraftServer server = event.getServer();
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
                                    "Gambling Items | NeoForge 1.21.1 | Playable: " + availableModes
                                    + " | Use a terminal or a station. Roadmap: " + plannedModes), false);
                            return 1;
                        }))));
        LOGGER.info("Gambling Items loaded. Playable: {}. Planned: {}", availableModes, plannedModes);
    }
}
