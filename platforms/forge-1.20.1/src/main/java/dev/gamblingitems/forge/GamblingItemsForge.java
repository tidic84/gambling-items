package dev.gamblingitems.forge;

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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bootstrap for Minecraft 1.20.1, where NeoForge is the Forge 47 line. Never imports client classes. */
@Mod(GamblingItemsForge.MOD_ID)
public final class GamblingItemsForge {
    public static final String MOD_ID = "gamblingitems";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * The shared code registers into the vanilla registries, as on Fabric and NeoForge. Forge 47
     * keeps those wrappers locked and only accepts its own events; during the registration phase,
     * when every registry is unfrozen anyway, they are opened for the time of that one call.
     */
    private static void registerAll() {
        var opened = new java.util.ArrayList<Object>();
        try {
            for (var registry : net.minecraft.core.registries.BuiltInRegistries.REGISTRY) {
                var field = findLocked(registry.getClass());
                if (field != null && field.getBoolean(registry)) {
                    field.setBoolean(registry, false);
                    opened.add(registry);
                }
            }
            ModContent.initialize();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot open the registries of Forge 47", exception);
        } finally {
            for (Object registry : opened) {
                try { findLocked(registry.getClass()).setBoolean(registry, true); } catch (ReflectiveOperationException ignored) {}
            }
        }
    }

    private static java.lang.reflect.Field findLocked(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                var field = current.getDeclaredField("locked");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    public GamblingItemsForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Every registry is open during the registration events, so the first one registers it all.
        modBus.addListener((RegisterEvent event) -> registerAll());
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) ModContent.creativeEntries().forEach(event::accept);
        });
        if (FMLEnvironment.dist.isClient()) dev.gamblingitems.forge.client.GamblingItemsForgeClient.register(modBus);

        IEventBus game = MinecraftForge.EVENT_BUS;
        // Keys are found on mobs, so the cases have a price that is played for, not bought.
        game.addListener((LootTableLoadEvent event) ->
                KeyDrops.poolsFor(event.getName()).forEach(pool -> event.getTable().addPool(pool.build())));
        game.addListener((ServerAboutToStartEvent event) -> ModConfig.load(event.getServer()));
        // Sent to every player at once only after a /reload succeeded.
        game.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) ModConfig.load(event.getPlayerList().getServer());
        });
        // Shared rounds live on the server, not in a block: a flight survives an unloaded chunk.
        game.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) return;
            MinecraftServer server = event.getServer();
            CrashGames.tick(server);
            RouletteGames.tick(server);
            BlackjackTables.tick(server);
            BattleLobbies.tick(server);
            BingoGames.tick(server);
            StationInteractions.tick(server);
        });
        // An interrupted round is cancelled and every engaged stake is given back exactly once.
        game.addListener((ServerStoppingEvent event) -> {
            MinecraftServer server = event.getServer();
            StationInteractions.stop(server);
            CrashGames.stopping(server);
            RouletteGames.stopping(server);
            BlackjackTables.stopping(server);
            BattleLobbies.stopping(server);
            BingoGames.stopping(server);
        });
        game.addListener((ServerStoppedEvent event) -> {
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
        game.addListener((RegisterCommandsEvent event) ->
                event.getDispatcher().register(Commands.literal("gamblingitems")
                        .then(Commands.literal("info").executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Gambling Items | Forge | Playable: " + availableModes
                                    + " | Use a terminal or a station. Roadmap: " + plannedModes), false);
                            return 1;
                        }))));
        LOGGER.info("Gambling Items loaded. Playable: {}. Planned: {}", availableModes, plannedModes);
        // Release checks start a real server once per Minecraft version; it stops when the world is up.
        if (Boolean.getBoolean("gamblingitems.smoke")) game.addListener((ServerStartedEvent event) -> {
            LOGGER.info("Gambling Items smoke test: server started");
            event.getServer().halt(false);
        });
    }
}
