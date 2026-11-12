package dev.gamblingitems.fabric.roulette;

import dev.gamblingitems.core.cheat.CheatRules.Tool;
import dev.gamblingitems.core.roulette.RouletteWheel.Bet;
import dev.gamblingitems.core.roulette.RouletteWheel.BetType;
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

/** A magnet drags the ball one pocket towards its owner's chips; spotted, those chips are seized. */
public class RouletteCheatGameTests implements FabricGameTest {
    private static final long IRON = 1_000;

    private static RouletteSetup setup() {
        return new RouletteSetup(new ValueCatalog(List.of(
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("iron_ingot"), IRON),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("diamond"), 10_000))),
                new RouletteSettings(IRON, 40, 20, 20));
    }

    /** Every draw of this wheel lands on zero, right between the red 32 and the black 26. */
    private static RouletteGame zero(GameTestHelper helper, ServerPlayer player) {
        return new RouletteGame(helper.getLevel(), player.blockPosition(), setup(), () -> 0L);
    }

    private static ServerPlayer onRed(GameTestHelper helper, RouletteGame[] game) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().add(new ItemStack(ModContent.POCKET_CASINO));
        player.getInventory().add(new ItemStack(ModContent.cheatTool(Tool.ROULETTE_MAGNET)));
        game[0] = zero(helper, player);
        Container vault = game[0].vault(player.getUUID());
        for (int slot = 0; slot < RouletteSettings.VAULT_SIZE; slot++) vault.setItem(slot, ItemStack.EMPTY);
        vault.setItem(RouletteSettings.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, 2));
        helper.assertTrue(game[0].place(player.getUUID(), new Bet(BetType.RED, 0), game[0].stagedValue(player.getUUID())),
                "Two ingots are placed on red");
        return player;
    }

    private static void spin(RouletteGame game) {
        for (int tick = 0; tick < 1_000 && game.phase() != RouletteGame.Phase.RESULT; tick++) game.tick();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anUnnoticedMagnetTurnsZeroIntoTheRedNextToIt(GameTestHelper helper) {
        CasinoSecurity.testing(bound -> bound - 1, null);
        try {
            RouletteGame[] game = new RouletteGame[1];
            ServerPlayer player = onRed(helper, game);
            UUID id = player.getUUID();
            RouletteMenu menu = new RouletteMenu(1, player.getInventory(), game[0].setup(), game[0].vault(id), game[0]);
            menu.broadcastChanges();
            helper.assertTrue(menu.canMagnet(), "The window offers the magnet to a player with chips on the felt");
            helper.assertTrue(menu.clickMenuButton(player, RouletteMenu.CHEAT_BUTTON), "The magnet is set");
            helper.assertTrue(game[0].magnetSet(id), "The round knows it");
            helper.assertFalse(menu.clickMenuButton(player, RouletteMenu.CHEAT_BUTTON), "One magnet per round");
            spin(game[0]);
            helper.assertTrue(game[0].resultNumber() == 32, "The ball is dragged from zero to the red 32");
            helper.assertTrue(game[0].seatOf(id).paid() == 4 * IRON, "Red pays twice the stake");
        } finally {
            CasinoSecurity.testing(null, null);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aSpottedMagnetSeizesTheChipsWhateverComesOut(GameTestHelper helper) {
        CasinoSecurity.testing(bound -> 0, null);
        try {
            RouletteGame[] game = new RouletteGame[1];
            ServerPlayer player = onRed(helper, game);
            UUID id = player.getUUID();
            RouletteMenu menu = new RouletteMenu(1, player.getInventory(), game[0].setup(), game[0].vault(id), game[0]);
            helper.assertTrue(menu.clickMenuButton(player, RouletteMenu.CHEAT_BUTTON), "The magnet is tried");
            helper.assertFalse(game[0].magnetSet(id), "A spotted magnet does nothing");
            helper.assertTrue(game[0].seatOf(id).settled() && game[0].seatOf(id).paid() == 0, "The seat is settled at nothing");
            helper.assertTrue(game[0].vault(id).getItem(RouletteSettings.ENGAGED_SLOT).isEmpty(), "The chips are seized");
            helper.assertTrue(CasinoSecurity.banned(player), "The cheater is banned");
            spin(game[0]);
            helper.assertTrue(game[0].resultNumber() == 0, "The ball stays where it fell");
            helper.assertTrue(game[0].winnings(game[0].vault(id)) == 0, "And nothing is paid afterwards");
        } finally {
            CasinoSecurity.testing(null, null);
        }
        helper.succeed();
    }
}
