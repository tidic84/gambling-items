package dev.gamblingitems.fabric.crash;

import dev.gamblingitems.core.crash.CrashRules;
import dev.gamblingitems.fabric.ModContent;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class CrashGameTests implements FabricGameTest {
    private static final int BETTING_TICKS = 40, RESULT_TICKS = 20;
    /** Draws are uniform in [0, DRAW_BOUND): this one lands the flight exactly on 2.50x. */
    private static final long DRAW_250 = 95L * CrashGame.DRAW_BOUND / 250 - 1;
    private static final long DRAW_INSTANT = CrashGame.DRAW_BOUND - 1;

    private static CrashSettings settings() {
        return new CrashSettings(ResourceLocation.withDefaultNamespace("diamond"), 1,
                9_500, 5_000, 10_200, BETTING_TICKS, RESULT_TICKS);
    }

    private static CrashGame game(GameTestHelper helper, long draw) {
        return new CrashGame(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), settings(), () -> draw);
    }

    private static Container staked(CrashGame game, UUID player, int count) {
        Container vault = game.vault(player);
        for (int slot = 0; slot < CrashSettings.VAULT_SIZE; slot++) vault.setItem(slot, ItemStack.EMPTY);
        vault.setItem(CrashSettings.INPUT_SLOT, new ItemStack(Items.DIAMOND, count));
        return vault;
    }

    private static int winnings(Container vault) {
        int total = 0;
        for (int slot = CrashSettings.FIRST_PAYOUT_SLOT; slot < CrashSettings.VAULT_SIZE; slot++) {
            total += vault.getItem(slot).getCount();
        }
        return total;
    }

    private static void tick(CrashGame game, int times) {
        for (int index = 0; index < times; index++) game.tick();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aBetIsEngagedOnceAndCashingOutPaysTheVisibleMultiplier(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CrashGame game = game(helper, 0);
        UUID uuid = player.getUUID();
        Container vault = staked(game, uuid, 7);
        helper.assertTrue(game.place(uuid, 5), "A bet inside the limits is accepted");
        helper.assertTrue(vault.getItem(CrashSettings.INPUT_SLOT).getCount() == 2, "Only the stake is engaged");
        helper.assertTrue(vault.getItem(CrashSettings.ENGAGED_SLOT).getCount() == 5, "The stake waits in the vault");
        helper.assertFalse(game.place(uuid, 2), "One bet per player and per round");
        helper.assertTrue(game.phase() == CrashGame.Phase.BETTING, "The first bet opens the window");
        tick(game, BETTING_TICKS);
        helper.assertTrue(game.phase() == CrashGame.Phase.FLYING, "The round takes off on its own");
        tick(game, 10);
        int visible = game.publicMultiplier();
        helper.assertTrue(game.cashOut(uuid), "Cashing out during the flight is accepted");
        helper.assertFalse(game.cashOut(uuid), "A settled bet is never paid twice");
        helper.assertTrue(vault.getItem(CrashSettings.ENGAGED_SLOT).isEmpty(), "The engaged stake is consumed");
        helper.assertTrue(winnings(vault) == settings().rules().payout(5, visible),
                "The payout includes the stake, rounded down");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aCrashTakesEveryStakeStillEngaged(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CrashGame game = game(helper, DRAW_INSTANT);
        UUID uuid = player.getUUID();
        Container vault = staked(game, uuid, 3);
        helper.assertTrue(game.place(uuid, 3), "Bet accepted");
        tick(game, BETTING_TICKS);
        helper.assertTrue(game.phase() == CrashGame.Phase.CRASHED, "The worst draw never leaves the ground");
        helper.assertTrue(game.publicMultiplier() == CrashRules.START, "It crashed at 1.00x");
        helper.assertFalse(game.cashOut(uuid), "A cash out after the flight is refused");
        helper.assertTrue(vault.getItem(CrashSettings.ENGAGED_SLOT).isEmpty(), "The stake is gone");
        helper.assertTrue(winnings(vault) == 0, "Nothing is paid");
        helper.assertTrue(game.betOf(uuid).lost(), "The bet is marked as lost");
        tick(game, RESULT_TICKS);
        helper.assertTrue(game.phase() == CrashGame.Phase.WAITING, "The table reopens on its own");
        helper.assertTrue(game.lastCrashPoint() == CrashRules.START, "The crash point stays public afterwards");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void oneTableSettlesEachSeatOnItsOwn(GameTestHelper helper) {
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        CrashGame game = game(helper, DRAW_250);
        UUID early = first.getUUID(), late = second.getUUID();
        Container earlyVault = staked(game, early, 4);
        Container lateVault = staked(game, late, 4);
        helper.assertTrue(game.place(early, 4) && game.place(late, 4), "Both bets accepted");
        helper.assertTrue(game.participants() == 2 && game.pot() == 8, "The table is public");
        tick(game, BETTING_TICKS);
        // Everyone sees the same flight, but each seat is settled separately.
        while (game.phase() == CrashGame.Phase.FLYING && game.publicMultiplier() < 200) game.tick();
        int visible = game.publicMultiplier();
        helper.assertTrue(game.cashOut(early), "One player leaves the flight");
        helper.assertFalse(game.betOf(late).settledMultiplier() > 0, "The other stays engaged");
        while (game.phase() == CrashGame.Phase.FLYING) game.tick();
        helper.assertTrue(game.lastCrashPoint() == 250, "This draw crashes at 2.50x");
        helper.assertTrue(game.betOf(early).settledMultiplier() == visible, "Paid at the multiplier that was shown");
        helper.assertTrue(winnings(earlyVault) == settings().rules().payout(4, visible), "The payout is stored");
        helper.assertTrue(game.betOf(late).lost(), "Riding to the crash wins nothing");
        helper.assertTrue(winnings(lateVault) == 0, "Nothing is paid to the lost bet");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void betsAreRefusedOutsideTheWindowAndOutsideTheLimits(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CrashGame game = game(helper, 0);
        UUID uuid = player.getUUID();
        Container vault = staked(game, uuid, 4);
        helper.assertTrue(game.largestStake(uuid) == 64, "Empty winnings allow a full stack");
        helper.assertFalse(game.place(uuid, 65), "More than one stack is refused");
        helper.assertFalse(game.place(uuid, 0), "Below the minimum stake");
        helper.assertFalse(game.place(uuid, 5), "More than the vault holds");
        vault.setItem(CrashSettings.INPUT_SLOT, new ItemStack(Items.EMERALD, 8));
        helper.assertFalse(game.place(uuid, 4), "Another material is not this table");
        vault.setItem(CrashSettings.INPUT_SLOT, new ItemStack(Items.DIAMOND, 4));
        helper.assertTrue(game.place(uuid, 4), "A valid bet is accepted");
        tick(game, BETTING_TICKS);
        helper.assertTrue(game.phase() == CrashGame.Phase.FLYING, "The flight started");
        ServerPlayer late = helper.makeMockServerPlayerInLevel();
        staked(game, late.getUUID(), 4);
        helper.assertFalse(game.place(late.getUUID(), 4), "A flight accepts no new bet");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void winningsAlreadyHeldLimitTheNextBet(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CrashGame game = game(helper, 0);
        UUID uuid = player.getUUID();
        Container vault = staked(game, uuid, 8);
        for (int slot = CrashSettings.FIRST_PAYOUT_SLOT; slot < CrashSettings.VAULT_SIZE; slot++) {
            vault.setItem(slot, new ItemStack(Items.DIAMOND, 64));
        }
        helper.assertTrue(game.winnings(vault) == CrashSettings.PAYOUT_SLOTS * 64, "The winnings are full");
        helper.assertTrue(game.largestStake(uuid) == 0, "Nothing more could be paid");
        helper.assertFalse(game.place(uuid, 8), "A win that could not be stored is refused before the bet");
        helper.assertTrue(vault.getItem(CrashSettings.INPUT_SLOT).getCount() == 8, "Nothing is engaged");
        for (int slot = CrashSettings.FIRST_PAYOUT_SLOT; slot < CrashSettings.VAULT_SIZE; slot++) {
            vault.setItem(slot, ItemStack.EMPTY);
        }
        helper.assertTrue(game.place(uuid, 8), "With room, the same bet is accepted");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anInterruptedRoundGivesEveryEngagedStakeBackOnce(GameTestHelper helper) {
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        CrashGame game = game(helper, 0);
        UUID engaged = first.getUUID(), cashed = second.getUUID();
        Container engagedVault = staked(game, engaged, 6);
        Container cashedVault = staked(game, cashed, 6);
        helper.assertTrue(game.place(engaged, 6) && game.place(cashed, 6), "Both bets accepted");
        tick(game, BETTING_TICKS + 5);
        long paid = settings().rules().payout(6, game.publicMultiplier());
        helper.assertTrue(game.cashOut(cashed), "One player cashed out before the interruption");
        game.cancel();
        helper.assertTrue(winnings(engagedVault) == 6, "The engaged stake comes back once");
        helper.assertTrue(engagedVault.getItem(CrashSettings.ENGAGED_SLOT).isEmpty(), "Nothing stays engaged");
        helper.assertTrue(winnings(cashedVault) == paid, "A settled cash out is not paid a second time");
        game.cancel();
        helper.assertTrue(winnings(engagedVault) == 6, "Cancelling twice refunds once");
        helper.assertTrue(game.phase() == CrashGame.Phase.WAITING, "The table is free again");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theRoundOutlivesItsStationAndItsChunk(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.CRASH_STATION);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CrashGame game = game(helper, 0);
        UUID uuid = player.getUUID();
        Container vault = staked(game, uuid, 2);
        helper.assertTrue(game.place(uuid, 2), "Bet accepted at the station");
        tick(game, BETTING_TICKS);
        helper.setBlock(relative, Blocks.AIR);
        // Breaking the station releases nothing: the flight belongs to the server.
        tick(game, 5);
        helper.assertTrue(game.phase() == CrashGame.Phase.FLYING, "The flight continues");
        helper.assertTrue(game.cashOut(uuid), "The bet can still be settled");
        helper.assertTrue(winnings(vault) >= 2, "The player is paid from the round, not from the block");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aTerminalOnlyJoinsATableAnnouncedNearby(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.CRASH_STATION);
        BlockPos pos = helper.absolutePos(relative);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        try {
            helper.assertTrue(CrashGames.nearest(player) == null, "A terminal never opens a table on its own");
            CrashGame hosted = CrashGames.host(helper.getLevel(), pos);
            helper.assertTrue(CrashGames.host(helper.getLevel(), pos) == hosted, "One table per station");
            helper.assertTrue(CrashGames.nearest(player) == hosted, "A nearby table is joinable");
            player.setPos(pos.getX() + CrashGames.REACH + 8, pos.getY() + 1, pos.getZ() + 0.5);
            helper.assertTrue(CrashGames.nearest(player) == null, "A distant table is not");
        } finally {
            CrashGames.clear();
        }
        helper.succeed();
    }
}
