package dev.gamblingitems.fabric.crash;

import dev.gamblingitems.core.cheat.CheatRules.Tool;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.cheat.CasinoSecurity;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A rigged watch vibrates while the flight is still going; spotted, the bet is lost at once. */
public class CrashCheatGameTests implements FabricGameTest {
    /** The flight of this draw ends at 2.50x. */
    private static final long DRAW_250 = 95L * CrashGame.DRAW_BOUND / 250 - 1;

    private static CrashGame game(GameTestHelper helper, ServerPlayer player) {
        CrashGame game = new CrashGame(helper.getLevel(), player.blockPosition(), new CrashSetup(new ValueCatalog(List.of(
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("diamond"), 1))),
                new CrashSettings(1, 9_500, 5_000, 10_200, 40, 20)), () -> DRAW_250);
        Container vault = game.vault(player.getUUID());
        for (int slot = 0; slot < CrashSettings.VAULT_SIZE; slot++) vault.setItem(slot, ItemStack.EMPTY);
        vault.setItem(CrashSettings.INPUT_SLOT, new ItemStack(Items.DIAMOND, 5));
        helper.assertTrue(game.place(player.getUUID(), 5), "Five diamonds are engaged");
        return game;
    }

    private static ServerPlayer wearer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().add(new ItemStack(ModContent.POCKET_CASINO));
        player.getInventory().add(new ItemStack(ModContent.cheatTool(Tool.CRASH_WATCH)));
        return player;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anUnnoticedWatchVibratesBeforeTheCrash(GameTestHelper helper) {
        CasinoSecurity.testing(bound -> bound - 1, null);
        try {
            ServerPlayer player = wearer(helper);
            UUID id = player.getUUID();
            CrashGame game = game(helper, player);
            CrashMenu menu = new CrashMenu(1, player.getInventory(), game.setup(), game.vault(id), game);
            menu.broadcastChanges();
            helper.assertTrue(menu.canWatch(), "The window offers the watch once the bet is placed");
            helper.assertTrue(menu.clickMenuButton(player, CrashMenu.CHEAT_BUTTON), "The watch is put on");
            helper.assertTrue(game.watch(id) == 1, "It waits");
            for (int tick = 0; tick < 2_000 && game.watch(id) != 2 && game.phase() != CrashGame.Phase.CRASHED; tick++) game.tick();
            helper.assertTrue(game.watch(id) == 2, "The watch vibrated");
            helper.assertTrue(game.phase() == CrashGame.Phase.FLYING, "While the flight was still going");
            helper.assertTrue(game.cashOut(id), "So the stake can still be saved");
        } finally {
            CasinoSecurity.testing(null, null);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aSpottedWatchLosesTheBet(GameTestHelper helper) {
        CasinoSecurity.testing(bound -> 0, null);
        try {
            ServerPlayer player = wearer(helper);
            UUID id = player.getUUID();
            CrashGame game = game(helper, player);
            CrashMenu menu = new CrashMenu(1, player.getInventory(), game.setup(), game.vault(id), game);
            helper.assertTrue(menu.clickMenuButton(player, CrashMenu.CHEAT_BUTTON), "The watch is tried");
            helper.assertTrue(game.betOf(id).lost(), "The bet is lost at once");
            helper.assertTrue(game.vault(id).getItem(CrashSettings.ENGAGED_SLOT).isEmpty(), "The stake is seized");
            helper.assertTrue(game.watch(id) == 0, "And the watch does nothing");
            helper.assertTrue(CasinoSecurity.banned(player), "The cheater is banned");
        } finally {
            CasinoSecurity.testing(null, null);
        }
        helper.succeed();
    }
}
