package dev.gamblingitems.fabric;

import dev.gamblingitems.core.GameMode;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import dev.gamblingitems.fabric.crash.CrashGames;
import dev.gamblingitems.fabric.roulette.RouletteGames;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.menu.GameMenus;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric-specific bootstrap. This class must never import client rendering classes. */
public final class GamblingItemsFabric implements ModInitializer {
    public static final String MOD_ID = "gamblingitems";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModContent.initialize();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> ModConfig.load());
        // Shared rounds live on the server, not in a block: a flight survives an unloaded chunk.
        ServerTickEvents.END_SERVER_TICK.register(CrashGames::tick);
        ServerTickEvents.END_SERVER_TICK.register(RouletteGames::tick);
        ServerTickEvents.END_SERVER_TICK.register(dev.gamblingitems.fabric.block.StationInteractions::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(dev.gamblingitems.fabric.block.StationInteractions::stop);
        // An interrupted round is cancelled and every engaged stake is given back exactly once.
        ServerLifecycleEvents.SERVER_STOPPING.register(CrashGames::stopping);
        ServerLifecycleEvents.SERVER_STOPPING.register(RouletteGames::stopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CrashGames.clear();
            RouletteGames.clear();
        });
        String availableModes = GameMenus.AVAILABLE.stream().map(GameMode::id).collect(Collectors.joining(", "));
        String plannedModes = Arrays.stream(GameMode.values())
                .filter(mode -> !GameMenus.AVAILABLE.contains(mode))
                .map(GameMode::id).collect(Collectors.joining(", "));
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
                dispatcher.register(Commands.literal("gamblingitems")
                        .then(Commands.literal("info").executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Gambling Items | Fabric 1.21.1 | Playable: " + availableModes
                                    + " | Use a terminal or a station. Roadmap: " + plannedModes), false);
                            return 1;
                        }))));
        LOGGER.info("Gambling Items loaded. Playable: {}. Planned: {}", availableModes, plannedModes);
    }
}
