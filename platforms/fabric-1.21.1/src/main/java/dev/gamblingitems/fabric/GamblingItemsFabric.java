package dev.gamblingitems.fabric;

import dev.gamblingitems.core.GameMode;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
//#if MC >= 1.21
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
//#else
//$ import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
//#endif
import dev.gamblingitems.fabric.crash.CrashGames;
import dev.gamblingitems.fabric.battle.BattleLobbies;
import dev.gamblingitems.fabric.bingo.BingoGames;
import dev.gamblingitems.fabric.blackjack.BlackjackTables;
import dev.gamblingitems.fabric.roulette.RouletteGames;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.loot.KeyDrops;
import dev.gamblingitems.fabric.menu.GameMenus;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric-specific bootstrap. This class must never import client rendering classes. */
public final class GamblingItemsFabric implements ModInitializer {
    public static final String MOD_ID = "gamblingitems";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModContent.initialize();
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(entries -> ModContent.creativeEntries().forEach(entries::accept));
        // Keys are found on mobs, so the cases have a price that is played for, not bought.
        //#if MC >= 1.21
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            if (source.isBuiltin()) KeyDrops.poolsFor(key).forEach(builder::withPool);
        });
        //#elif MC >= 1.20.5
        //$ LootTableEvents.MODIFY.register((key, builder, source) -> {
        //$     if (source.isBuiltin()) KeyDrops.poolsFor(key).forEach(builder::withPool);
        //$ });
        //#else
        //$ LootTableEvents.MODIFY.register((resources, loot, id, builder, source) -> {
        //$     if (source.isBuiltin()) KeyDrops.poolsFor(id).forEach(builder::withPool);
        //$ });
        //#endif
        ServerLifecycleEvents.SERVER_STARTING.register(ModConfig::load);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) ModConfig.load(server);
        });
        // Shared rounds live on the server, not in a block: a flight survives an unloaded chunk.
        ServerTickEvents.END_SERVER_TICK.register(CrashGames::tick);
        ServerTickEvents.END_SERVER_TICK.register(RouletteGames::tick);
        ServerTickEvents.END_SERVER_TICK.register(BlackjackTables::tick);
        ServerTickEvents.END_SERVER_TICK.register(BattleLobbies::tick);
        ServerTickEvents.END_SERVER_TICK.register(BingoGames::tick);
        ServerTickEvents.END_SERVER_TICK.register(dev.gamblingitems.fabric.block.StationInteractions::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(dev.gamblingitems.fabric.block.StationInteractions::stop);
        // An interrupted round is cancelled and every engaged stake is given back exactly once.
        ServerLifecycleEvents.SERVER_STOPPING.register(CrashGames::stopping);
        ServerLifecycleEvents.SERVER_STOPPING.register(RouletteGames::stopping);
        ServerLifecycleEvents.SERVER_STOPPING.register(BlackjackTables::stopping);
        ServerLifecycleEvents.SERVER_STOPPING.register(BattleLobbies::stopping);
        ServerLifecycleEvents.SERVER_STOPPING.register(BingoGames::stopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
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
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
                dispatcher.register(Commands.literal("gamblingitems")
                        .then(Commands.literal("info").executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Gambling Items | Fabric | Playable: " + availableModes
                                    + " | Use a terminal or a station. Roadmap: " + plannedModes), false);
                            return 1;
                        }))));
        LOGGER.info("Gambling Items loaded. Playable: {}. Planned: {}", availableModes, plannedModes);
        // Release checks start a real server once per Minecraft version; it stops when the world is up.
        if (Boolean.getBoolean("gamblingitems.smoke")) ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("Gambling Items smoke test: server started");
            server.halt(false);
        });
    }
}
