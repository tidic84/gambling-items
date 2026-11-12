package dev.gamblingitems.fabric.prediction;

import dev.gamblingitems.core.prediction.PredictionMarket;
import dev.gamblingitems.core.prediction.PredictionMarket.Side;
import dev.gamblingitems.core.prediction.PredictionMarket.Status;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.cheat.CasinoSecurity;
import dev.gamblingitems.fabric.value.ItemBank;
import dev.gamblingitems.fabric.value.ValueCatalog;
import dev.gamblingitems.fabric.vault.CasinoLedger;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A question is traded with physical tickets, settled by the game when it sees the answer, answered
 * by its creator when it is written by hand, and watched by the commission when a player bets on
 * their own failure.
 */
public class PredictionGameTests implements FabricGameTest {
    private static final long IRON = 1_000;

    /** A quiet market: nothing announced, no question opened by the house. */
    private static PredictionSetup quiet() {
        return new PredictionSetup(new ValueCatalog(List.of(
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("iron_ingot"), IRON),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("diamond"), 10_000))),
                new PredictionSettings(IRON, 200, 5_000, 5, 600_000, 3_600_000, 86_400_000, 1_209_600_000L,
                        false, 6_000, 30, false, 1_200_000, 3, 10_000, 2_700_000));
    }

    private interface Scene { void play(MinecraftServer server); }

    /** Runs a scene with the quiet market and puts the server's own market back afterwards. */
    private static void withQuietMarket(GameTestHelper helper, Scene scene) {
        PredictionSetup before = PredictionMarkets.setup();
        PredictionMarkets.configure(quiet());
        try {
            scene.play(helper.getLevel().getServer());
        } finally {
            PredictionMarkets.configure(before);
            CasinoSecurity.testing(null, null);
        }
        helper.succeed();
    }

    private static MarketRecord deaths(GameTestHelper helper, MinecraftServer server, ServerPlayer creator, ServerPlayer subject) {
        PredictionMarkets.Opening opening = PredictionMarkets.open(server, creator.getUUID(), "creator", MarketKind.DEATHS,
                subject, "", 1, "", 60, 10_000);
        helper.assertTrue(opening.record() != null, "The question opens: " + opening.refusal());
        return opening.record();
    }

    private static ServerPlayer trader(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().add(new ItemStack(ModContent.POCKET_CASINO));
        return player;
    }

    private static boolean select(PredictionMenu menu, ServerPlayer player, int id) {
        for (int page = 0; page < 20; page++) {
            menu.broadcastChanges();
            for (int row = 0; row < PredictionMenu.ROWS; row++) {
                MarketView view = menu.row(row);
                if (view != null && view.id() == id) return menu.clickMenuButton(player, PredictionMenu.FIRST_SELECT + row);
            }
            if (!menu.clickMenuButton(player, PredictionMenu.PAGE_NEXT)) return false;
        }
        return false;
    }

    private static ItemStack firstPayout(SimpleContainer vault) {
        for (int slot = PredictionSettings.FIRST_PAYOUT_SLOT; slot < PredictionSettings.VAULT_SIZE; slot++) {
            if (!vault.getItem(slot).isEmpty()) return vault.getItem(slot);
        }
        return ItemStack.EMPTY;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aTicketIsBoughtSoldAndCashedThroughTheWindow(GameTestHelper helper) {
        withQuietMarket(helper, server -> {
            ServerPlayer creator = trader(helper), subject = trader(helper), bettor = trader(helper);
            MarketRecord record = deaths(helper, server, creator, subject);
            SimpleContainer vault = new SimpleContainer(PredictionSettings.VAULT_SIZE);
            PredictionMenu menu = new PredictionMenu(1, bettor.getInventory(), quiet(), vault, ContainerLevelAccess.NULL);
            helper.assertTrue(select(menu, bettor, record.id()), "The question is listed and selected");
            vault.setItem(PredictionSettings.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, 5));
            menu.broadcastChanges();
            helper.assertTrue(menu.quote(Side.YES) > 0, "The window quotes what the stake buys");
            helper.assertTrue(menu.clickMenuButton(bettor, PredictionMenu.BUY_YES), "YES is bought");
            helper.assertTrue(vault.getItem(PredictionSettings.INPUT_SLOT).isEmpty(), "The whole stake was spent");
            Tickets.Ticket ticket = Tickets.read(firstPayout(vault));
            helper.assertTrue(ticket != null && ticket.side() == Side.YES && ticket.buyer().equals(bettor.getUUID()),
                    "A ticket waits with the winnings");
            helper.assertTrue(record.market().priceBasisPoints() > 5_000, "Buying YES raised its price");
            helper.assertTrue(menu.clickMenuButton(bettor, PredictionMenu.COLLECT), "The ticket is collected");
            helper.assertTrue(bettor.getInventory().countItem(ModContent.PREDICTION_TICKET) == 1, "It is a real item");

            // The subject dies once: the game sees it and settles the question by itself.
            subject.awardStat(Stats.DEATHS);
            PredictionMarkets.check(server);
            helper.assertTrue(record.market().status() == Status.RESOLVED && record.market().outcome() == Side.YES,
                    "One death settles the question on YES");
            helper.assertTrue(CasinoLedger.get(server).credit(creator.getUUID()) >= 100,
                    "The creator is owed at least the fee");

            int slot = -1;
            for (int index = 0; index < bettor.getInventory().getContainerSize(); index++)
                if (bettor.getInventory().getItem(index).is(ModContent.PREDICTION_TICKET)) slot = index;
            helper.assertTrue(slot >= 0, "The ticket is in the inventory");
            vault.setItem(PredictionSettings.TICKET_SLOT, bettor.getInventory().removeItem(slot, 1));
            menu.broadcastChanges();
            helper.assertTrue(menu.ticketState() == PredictionMarkets.Quote.REDEEM, "The ticket can be cashed");
            long owed = ticket.shares() * IRON / PredictionMarket.MILLI;
            helper.assertTrue(menu.clickMenuButton(bettor, PredictionMenu.CASH), "The ticket is cashed");
            long paid = ItemBank.valueOf(quiet().catalog(), vault, PredictionSettings.FIRST_PAYOUT_SLOT, PredictionSettings.VAULT_SIZE);
            helper.assertTrue(paid <= owed && paid > owed - IRON, "Every winning share is paid, in items: " + paid + " of " + owed);
            helper.assertTrue(vault.getItem(PredictionSettings.TICKET_SLOT).isEmpty(), "A cashed ticket is gone");
            helper.assertFalse(menu.clickMenuButton(bettor, PredictionMenu.CASH), "And cannot be cashed twice");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aTicketSoldBackBeforeTheEndReturnsAboutWhatItCost(GameTestHelper helper) {
        withQuietMarket(helper, server -> {
            ServerPlayer creator = trader(helper), subject = trader(helper);
            MarketRecord record = deaths(helper, server, creator, subject);
            PredictionMarket.Purchase purchase = PredictionMarkets.buy(server, record, Side.NO, 5_000);
            helper.assertTrue(purchase != null, "NO is bought");
            ItemStack stack = Tickets.create(record, purchase, subject.getUUID());
            PredictionMarkets.Cashing cashing = PredictionMarkets.cash(server, Tickets.read(stack));
            helper.assertTrue(cashing.done() && !cashing.seized(), "An open question buys its tickets back");
            helper.assertTrue(cashing.value() <= purchase.net() && cashing.value() >= purchase.net() - 10,
                    "Only the fee and the rounding are lost");
            helper.assertTrue(record.market().priceBasisPoints() == 5_000, "The price is back where it started");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theCommissionSeizesABetAgainstOneself(GameTestHelper helper) {
        withQuietMarket(helper, server -> {
            ServerPlayer creator = trader(helper), subject = trader(helper);
            MarketRecord record = deaths(helper, server, creator, subject);
            // Betting that you will die is betting on your own failure.
            PredictionMarket.Purchase purchase = PredictionMarkets.buy(server, record, Side.YES, 5_000);
            ItemStack ticket = Tickets.create(record, purchase, subject.getUUID());
            helper.assertTrue(PredictionMarkets.judge(server, record, Side.YES), "The question is settled on YES");
            CasinoSecurity.testing(bound -> 0, null);
            PredictionMarkets.Cashing cashing = PredictionMarkets.cash(server, Tickets.read(ticket));
            helper.assertTrue(cashing.done() && cashing.seized() && cashing.value() == 0, "The commission seizes the ticket");
            helper.assertTrue(CasinoSecurity.banned(subject), "And bans the player who bought it");
            // Given to someone else, the ticket still remembers who bought it.
            helper.assertFalse(PredictionMarkets.cash(server, Tickets.read(ticket)).done(), "A seized ticket pays nothing more");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anUnnoticedBetAgainstOneselfPaysButLeavesSuspicion(GameTestHelper helper) {
        withQuietMarket(helper, server -> {
            ServerPlayer creator = trader(helper), subject = trader(helper);
            MarketRecord record = deaths(helper, server, creator, subject);
            PredictionMarket.Purchase purchase = PredictionMarkets.buy(server, record, Side.YES, 5_000);
            ItemStack ticket = Tickets.create(record, purchase, subject.getUUID());
            PredictionMarkets.judge(server, record, Side.YES);
            CasinoSecurity.testing(bound -> bound - 1, null);
            PredictionMarkets.Cashing cashing = PredictionMarkets.cash(server, Tickets.read(ticket));
            helper.assertTrue(cashing.done() && !cashing.seized(), "Nobody noticed");
            helper.assertTrue(cashing.value() == purchase.shares() * IRON / PredictionMarket.MILLI, "The ticket pays in full");
            helper.assertTrue(CasinoSecurity.heat(server, subject.getUUID()) == 30, "But suspicion stays");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aWrittenQuestionIsAnsweredByItsCreatorAndCanBeDisputed(GameTestHelper helper) {
        withQuietMarket(helper, server -> {
            long[] now = {1_000_000};
            CasinoSecurity.testing(null, () -> now[0]);
            ServerPlayer creator = trader(helper), other = trader(helper);
            MarketRecord undisputed = PredictionMarkets.open(server, creator.getUUID(), "creator", MarketKind.CUSTOM,
                    null, "", 0, "Will the bridge be finished?", 10, 5_000).record();
            MarketRecord disputed = PredictionMarkets.open(server, creator.getUUID(), "creator", MarketKind.CUSTOM,
                    null, "", 0, "§cWill   it rain?", 10, 5_000).record();
            helper.assertTrue(undisputed != null && disputed != null, "Both questions open");
            helper.assertTrue(disputed.question().equals("Will it rain?"), "Formatting codes and extra spaces are dropped");
            helper.assertTrue(PredictionMarkets.open(server, creator.getUUID(), "creator", MarketKind.CUSTOM, null, "", 0,
                    "Hi", 10, 5_000).refusal().equals("no_question"), "A question needs some words");
            helper.assertFalse(PredictionMarkets.propose(server, other, undisputed, Side.YES), "Only the creator answers");

            now[0] += 11 * 60_000;
            PredictionMarkets.check(server);
            helper.assertTrue(undisputed.market().status() == Status.CLOSED, "Trading stops at the deadline");
            helper.assertTrue(PredictionMarkets.propose(server, creator, undisputed, Side.YES), "The creator answers");
            helper.assertTrue(PredictionMarkets.propose(server, creator, disputed, Side.NO), "Both of them");
            helper.assertFalse(PredictionMarkets.dispute(server, creator, disputed), "A creator cannot dispute their own answer");
            helper.assertTrue(PredictionMarkets.dispute(server, other, disputed), "Anyone else can");

            now[0] += 10 * 60_000;
            PredictionMarkets.check(server);
            helper.assertTrue(undisputed.market().status() == Status.RESOLVED && undisputed.market().outcome() == Side.YES,
                    "An undisputed answer stands once its window has passed");
            helper.assertTrue(disputed.market().status() == Status.DISPUTED, "A disputed one waits for an operator");
            helper.assertTrue(PredictionMarkets.judge(server, disputed, Side.YES), "The operator decides");
            helper.assertTrue(disputed.market().outcome() == Side.YES, "Against the creator's answer");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aTicketKeepsItsPositionAndItsBuyerAsItemData(GameTestHelper helper) {
        withQuietMarket(helper, server -> {
            ServerPlayer creator = trader(helper), subject = trader(helper);
            MarketRecord record = deaths(helper, server, creator, subject);
            PredictionMarket.Purchase purchase = PredictionMarkets.buy(server, record, Side.NO, 2_500);
            ItemStack stack = Tickets.create(record, purchase, creator.getUUID());
            ItemStack copy = stack.copy();
            Tickets.Ticket ticket = Tickets.read(copy);
            helper.assertTrue(ticket.market() == record.id() && ticket.side() == Side.NO
                    && ticket.shares() == purchase.shares() && ticket.buyer().equals(creator.getUUID()),
                    "A copied ticket still describes the same position");
            helper.assertTrue(Tickets.read(new ItemStack(Items.PAPER)) == null, "A paper is not a ticket");
            CasinoLedger reloaded = CasinoLedger.load(CasinoLedger.get(server).save(new net.minecraft.nbt.CompoundTag(),
                    server.registryAccess()), server.registryAccess());
            MarketRecord saved = reloaded.market(record.id());
            helper.assertTrue(saved != null && saved.market().noShares() == record.market().noShares()
                    && saved.market().pool() == record.market().pool(), "A saved question comes back exactly");
        });
    }
}
