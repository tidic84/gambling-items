package dev.gamblingitems.fabric.vault;

import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;

/** The arcade ledger: tickets counted once a play is shown, spent at the counter, saved whole. */
public class ArcadeLedgerGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void ticketsCountOnlyOnceThePlayIsShown(GameTestHelper helper) {
        ArcadeLedger ledger = new ArcadeLedger(() -> {});
        UUID player = UUID.randomUUID();
        ledger.record(player, "Alex", "plinko", 1_000, 1_100, 110, 100);
        ledger.record(player, "Alex", "big_six", 1_000, 0, 0, 120);
        helper.assertTrue(ledger.balance(player, 99) == 0, "A ball still falling gives nothing away");
        helper.assertTrue(ledger.history(player, 99).isEmpty() && ledger.leaderboard(99).isEmpty(), "Nor does the history");
        helper.assertTrue(ledger.balance(player, 100) == 110, "Once shown, the tickets are there");
        helper.assertTrue(ledger.history(player, 200).size() == 2 && ledger.leaderboard(200).size() == 1,
                "Every play is in the history; only wins go on the board");
        helper.assertFalse(ledger.spend(player, 111, 200), "No more can be spent than won");
        helper.assertTrue(ledger.spend(player, 100, 200) && ledger.balance(player, 200) == 10, "A prize takes its price");
        ArcadeLedger read = new ArcadeLedger(() -> {});
        CompoundTag saved = ledger.write();
        read.read(saved);
        helper.assertTrue(read.balance(player, 200) == 10 && read.history(player, 200).size() == 2, "The ledger is saved whole");
        helper.succeed();
    }
}
