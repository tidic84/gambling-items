package dev.gamblingitems.fabric;

import dev.gamblingitems.core.GameMode;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import dev.gamblingitems.fabric.upgrade.UpgradeConfig;
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
        ServerLifecycleEvents.SERVER_STARTING.register(server -> UpgradeConfig.load());
        String plannedModes = Arrays.stream(GameMode.values())
                .map(GameMode::id).collect(Collectors.joining(", "));
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
                dispatcher.register(Commands.literal("gamblingitems")
                        .then(Commands.literal("info").executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Gambling Items | Fabric 1.21.1 | Upgrader available: use a terminal or upgrade station. "
                                    + "Roadmap: " + plannedModes), false);
                            return 1;
                        }))));
        LOGGER.info("Gambling Items core loaded. Planned modes: {}", plannedModes);
    }
}
