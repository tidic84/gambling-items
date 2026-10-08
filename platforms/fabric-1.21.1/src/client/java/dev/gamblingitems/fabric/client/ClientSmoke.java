package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.menu.GameMenus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Release check for the interface of each Minecraft version: in a singleplayer world, opens every
 * game from its portable item, saves a screenshot of each window, then quits. Only active with
 * -Dgamblingitems.clientsmoke=true.
 */
public final class ClientSmoke {
    public static final boolean ENABLED = Boolean.getBoolean("gamblingitems.clientsmoke");
    private static final int STEP = 60, SETTLE = 40;
    private static final List<GameMode> MODES = new ArrayList<>();
    private static int ticks = -1;

    static {
        MODES.addAll(GameMenus.TERMINAL);
        MODES.addAll(GameMenus.CASINO);
    }

    private ClientSmoke() {}

    public static void tick(Minecraft minecraft) {
        if (!ENABLED || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        ticks++;
        var server = minecraft.getSingleplayerServer();
        var id = minecraft.player.getUUID();
        if (ticks == 20) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) return;
            player.getInventory().add(new ItemStack(ModContent.TERMINAL));
            player.getInventory().add(new ItemStack(ModContent.POCKET_CASINO));
            player.getInventory().add(new ItemStack(Items.DIAMOND, 20));
            player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 10));
        });
        int step = (ticks - 40) / STEP, phase = (ticks - 40) % STEP;
        if (ticks < 40) return;
        if (step >= MODES.size()) {
            minecraft.stop();
            return;
        }
        GameMode mode = MODES.get(step);
        if (phase == 0) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) GameMenus.open(player, mode, ContainerLevelAccess.NULL);
        });
        if (phase == SETTLE) {
            String name = "gamblingitems-smoke-" + mode.id() + (minecraft.screen instanceof CasinoScreen<?> ? "" : "-NOSCREEN") + ".png";
            //#if MC >= 1.21.6
            //$ Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(), 1, message -> {});
            //#else
            Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(), message -> {});
            //#endif
        }
    }
}
