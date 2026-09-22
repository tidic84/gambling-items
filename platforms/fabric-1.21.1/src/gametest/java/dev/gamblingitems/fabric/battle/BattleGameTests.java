package dev.gamblingitems.fabric.battle;

import dev.gamblingitems.core.battle.BattleRules;
import dev.gamblingitems.core.cases.CaseRarity;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.cases.CaseDefinition;
import dev.gamblingitems.fabric.config.ModConfig;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class BattleGameTests implements FabricGameTest {
    private static final int LOBBY_TICKS = 100, ROUND_TICKS = 10, RESULT_TICKS = 20;

    private static BattleSetup setup() {
        return new BattleSetup(ModConfig.cases(), new BattleSettings(LOBBY_TICKS, ROUND_TICKS, RESULT_TICKS));
    }

    private static BattleLobby lobby(GameTestHelper helper, long draw) {
        return new BattleLobby(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), setup(), () -> draw);
    }

    private static CaseDefinition caseOf(CaseRarity rarity) {
        for (CaseDefinition definition : ModConfig.cases().cases().cases()) {
            if (definition.id().equals(rarity.caseId())) return definition;
        }
        throw new IllegalStateException("The " + rarity.id() + " case is missing");
    }

    private static Container keys(BattleLobby lobby, UUID player, CaseRarity rarity, int count) {
        Container vault = lobby.vault(player);
        for (int slot = 0; slot < BattleSettings.VAULT_SIZE; slot++) vault.setItem(slot, ItemStack.EMPTY);
        vault.setItem(BattleSettings.INPUT_SLOT, new ItemStack(ModContent.key(rarity), count));
        return vault;
    }

    private static int keysIn(Container vault, int from, int to) {
        int count = 0;
        for (int slot = from; slot < to; slot++) count += vault.getItem(slot).getCount();
        return count;
    }

    private static void tick(BattleLobby lobby, int times) {
        for (int index = 0; index < times; index++) lobby.tick();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aSeatPaysItsEntryInKeysAndCanLeaveBeforeTheStart(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BattleLobby lobby = lobby(helper, 0);
        UUID uuid = player.getUUID();
        helper.assertTrue(lobby.choose(caseOf(CaseRarity.COMMON), 2), "The host picks the case and the rounds");
        Container vault = keys(lobby, uuid, CaseRarity.COMMON, 3);
        helper.assertTrue(lobby.entry().getCount() == 2, "One key per round");
        helper.assertTrue(lobby.join(uuid, "first"), "The entry is paid");
        helper.assertTrue(keysIn(vault, BattleSettings.ENGAGED_SLOT, BattleSettings.FIRST_PAYOUT_SLOT) == 2,
                "Exactly the entry is reserved");
        helper.assertTrue(keysIn(vault, BattleSettings.INPUT_SLOT, BattleSettings.ENGAGED_SLOT) == 1,
                "The spare key stays with its owner");
        helper.assertFalse(lobby.join(uuid, "first"), "One seat per player");
        helper.assertTrue(lobby.leave(uuid), "A seat may leave while the lobby is open");
        helper.assertTrue(keysIn(vault, BattleSettings.INPUT_SLOT, BattleSettings.ENGAGED_SLOT) == 3,
                "The entry comes back once");
        helper.assertTrue(lobby.players() == 0, "The lobby is empty again");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anEntryThatCannotBePaidIsRefused(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BattleLobby lobby = lobby(helper, 0);
        UUID uuid = player.getUUID();
        helper.assertTrue(lobby.choose(caseOf(CaseRarity.RARE), 3), "Three rounds of the rare case");
        Container vault = keys(lobby, uuid, CaseRarity.RARE, 2);
        helper.assertFalse(lobby.join(uuid, "short"), "Two keys do not pay for three rounds");
        helper.assertTrue(keysIn(vault, BattleSettings.INPUT_SLOT, BattleSettings.ENGAGED_SLOT) == 2,
                "Nothing is taken");
        vault.setItem(BattleSettings.INPUT_SLOT, new ItemStack(ModContent.key(CaseRarity.COMMON), 8));
        helper.assertFalse(lobby.join(uuid, "wrong"), "The key of another case is not an entry");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theBestTotalTakesEveryItemOpened(GameTestHelper helper) {
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        BattleLobby lobby = lobby(helper, 0);
        UUID one = first.getUUID(), two = second.getUUID();
        helper.assertTrue(lobby.choose(caseOf(CaseRarity.COMMON), 2), "Two rounds");
        Container firstVault = keys(lobby, one, CaseRarity.COMMON, 2);
        Container secondVault = keys(lobby, two, CaseRarity.COMMON, 2);
        helper.assertTrue(lobby.join(one, "one") && lobby.join(two, "two"), "Both seats are paid");
        helper.assertTrue(lobby.ready(), "Two seats are enough");
        helper.assertTrue(lobby.start(), "The battle starts");
        helper.assertFalse(lobby.join(helper.makeMockServerPlayerInLevel().getUUID(), "late"),
                "A running battle takes no new seat");
        tick(lobby, ROUND_TICKS * 2 + 2);
        helper.assertTrue(lobby.phase() == BattleLobby.Phase.DONE, "The battle played itself out");
        helper.assertTrue(lobby.winner() != null, "It has a winner");
        for (BattleLobby.Seat seat : lobby.seats()) {
            helper.assertTrue(seat.opened().size() == 2, "Every seat opened every round");
        }
        Container winner = lobby.winner().equals(one) ? firstVault : secondVault;
        Container loser = lobby.winner().equals(one) ? secondVault : firstVault;
        helper.assertTrue(keysIn(winner, BattleSettings.FIRST_PAYOUT_SLOT, BattleSettings.VAULT_SIZE) > 0,
                "The winner holds the prizes");
        helper.assertTrue(keysIn(loser, BattleSettings.FIRST_PAYOUT_SLOT, BattleSettings.VAULT_SIZE) == 0,
                "The loser is paid nothing");
        helper.assertTrue(keysIn(loser, BattleSettings.ENGAGED_SLOT, BattleSettings.FIRST_PAYOUT_SLOT) == 0,
                "And its entry stayed with the table");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anInterruptedBattleGivesEveryEntryBackOnce(GameTestHelper helper) {
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        BattleLobby lobby = lobby(helper, 0);
        UUID one = first.getUUID(), two = second.getUUID();
        helper.assertTrue(lobby.choose(caseOf(CaseRarity.COMMON), 4), "Four rounds");
        Container firstVault = keys(lobby, one, CaseRarity.COMMON, 4);
        keys(lobby, two, CaseRarity.COMMON, 4);
        helper.assertTrue(lobby.join(one, "one") && lobby.join(two, "two"), "Both seats are paid");
        helper.assertTrue(lobby.start(), "The battle starts");
        tick(lobby, ROUND_TICKS + 1);
        lobby.cancel();
        helper.assertTrue(keysIn(firstVault, BattleSettings.INPUT_SLOT, BattleSettings.ENGAGED_SLOT) == 4,
                "The entry comes back once");
        lobby.cancel();
        helper.assertTrue(keysIn(firstVault, BattleSettings.INPUT_SLOT, BattleSettings.ENGAGED_SLOT) == 4,
                "Cancelling twice refunds once");
        helper.assertTrue(lobby.phase() == BattleLobby.Phase.LOBBY, "The lobby is free again");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aLobbyNeedsTwoSeatsAndKeepsRunningWithoutItsStation(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.BATTLE_STATION);
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        BattleLobby lobby = lobby(helper, 0);
        helper.assertTrue(lobby.choose(caseOf(CaseRarity.COMMON), 1), "One round");
        keys(lobby, first.getUUID(), CaseRarity.COMMON, 1);
        keys(lobby, second.getUUID(), CaseRarity.COMMON, 1);
        helper.assertTrue(lobby.join(first.getUUID(), "one"), "One seat");
        helper.assertFalse(lobby.ready(), "A battle needs an opponent");
        helper.assertFalse(lobby.start(), "So it cannot start");
        helper.assertTrue(lobby.join(second.getUUID(), "two"), "A second seat");
        helper.assertTrue(lobby.start(), "Now it can start");
        helper.setBlock(relative, Blocks.AIR);
        tick(lobby, ROUND_TICKS + 2);
        helper.assertTrue(lobby.phase() == BattleLobby.Phase.DONE, "Breaking the station stops nothing");
        helper.assertTrue(lobby.winner() != null, "The battle still has a winner");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aLobbyOnlyHoldsFourSeats(GameTestHelper helper) {
        BattleLobby lobby = lobby(helper, 0);
        helper.assertTrue(lobby.choose(caseOf(CaseRarity.COMMON), 1), "One round");
        for (int seat = 0; seat < BattleRules.MAX_PLAYERS; seat++) {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            keys(lobby, player.getUUID(), CaseRarity.COMMON, 1);
            helper.assertTrue(lobby.join(player.getUUID(), "seat" + seat), "Seat " + seat + " is free");
        }
        ServerPlayer extra = helper.makeMockServerPlayerInLevel();
        keys(lobby, extra.getUUID(), CaseRarity.COMMON, 1);
        helper.assertFalse(lobby.join(extra.getUUID(), "fifth"), "A fifth seat is refused");
        helper.assertTrue(lobby.players() == BattleRules.MAX_PLAYERS, "The lobby is full");
        helper.assertFalse(lobby.choose(caseOf(CaseRarity.EPIC), 2), "The case cannot change once seats are paid");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aLobbyIsFilledFromTheBlockInFrontOfEveryone(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.BATTLE_STATION);
        var station = (dev.gamblingitems.fabric.block.GameStationEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        BattleLobby lobby = BattleLobbies.host(helper.getLevel(), station.getBlockPos());
        try {
            helper.assertTrue(lobby.choose(caseOf(CaseRarity.COMMON), 1), "One round");
            for (ServerPlayer player : new ServerPlayer[] {first, second}) {
                player.setPos(station.getBlockPos().getCenter());
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(ModContent.key(CaseRarity.COMMON), 1));
                dev.gamblingitems.fabric.block.StationInteractions.click(player, station, 1);
                dev.gamblingitems.fabric.block.StationInteractions.click(player, station, 3);
            }
            helper.assertTrue(lobby.players() == 2, "Both seats joined from the block");
            helper.assertTrue(first.containerMenu == first.inventoryMenu, "No inventory was opened");
            helper.assertTrue(station.publicBets.contains("0"), "The seats are shown to everyone");
        } finally {
            dev.gamblingitems.fabric.block.StationInteractions.close(first.getUUID());
            dev.gamblingitems.fabric.block.StationInteractions.close(second.getUUID());
            lobby.cancel();
            BattleLobbies.clear();
        }
        helper.succeed();
    }
}
