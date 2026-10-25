package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.menu.GameMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import dev.gamblingitems.fabric.machines.MachineMenu;
import net.minecraft.world.level.block.Block;
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
    // Machines are only opened at their block: the smoke places one in front of the player and plays it once.
    private static final Map<GameMode, Block> MACHINES = Map.of(GameMode.LUCKY_WHEEL, ModContent.LUCKY_WHEEL,
            GameMode.COIN_PUSHER, ModContent.COIN_PUSHER, GameMode.PRIZE_CLAW, ModContent.PRIZE_CLAW,
            GameMode.PACHINKO, ModContent.PACHINKO, GameMode.CAPSULE_GACHA, ModContent.CAPSULE_GACHA,
            GameMode.HIGH_STAKES, ModContent.HIGH_STAKES);

    static {
        MODES.addAll(GameMenus.TERMINAL);
        MODES.addAll(GameMenus.CASINO);
        MODES.addAll(GameMenus.MACHINES);
    }

    private ClientSmoke() {}

    public static void tick(Minecraft minecraft) {
        if (!ENABLED || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        ticks++;
        if (SmokeGallery.ENABLED) {
            SmokeGallery.tick(minecraft, ticks);
            return;
        }
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
            if (player == null) return;
            Block machine = MACHINES.get(mode);
            if (machine == null) {
                GameMenus.open(player, mode, ContainerLevelAccess.NULL);
                return;
            }
            var pos = player.blockPosition().relative(player.getDirection(), 2);
            player.level().setBlockAndUpdate(pos, machine.defaultBlockState());
            GameMenus.open(player, mode, ContainerLevelAccess.create(player.level(), pos));
        });
        if (phase == SETTLE / 2) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null || !(player.containerMenu instanceof MachineMenu menu)) return;
            menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 2));
            menu.clickMenuButton(player, MachineMenu.PLAY_BUTTON);
        });
        if (phase == SETTLE) {
            shot(minecraft, "gamblingitems-smoke-" + mode.id() + (minecraft.screen instanceof CasinoScreen<?> ? "" : "-NOSCREEN") + ".png");
        }
    }

    static void shot(Minecraft minecraft, String name) {
        //#if MC >= 1.21.6
        //$ Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(), 1, message -> {});
        //#else
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(), message -> {});
        //#endif
    }
}
