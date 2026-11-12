package dev.gamblingitems.fabric.prediction;

import dev.gamblingitems.core.prediction.PredictionMarket;
import dev.gamblingitems.core.prediction.PredictionMarket.Side;
import dev.gamblingitems.core.prediction.PredictionMarket.Status;
import dev.gamblingitems.fabric.cheat.CasinoSecurity;
import dev.gamblingitems.fabric.vault.CasinoLedger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.block.Blocks;

/**
 * The prediction market of a server: questions about its players, priced by a market maker, settled
 * by the game itself whenever it can see the answer.
 *
 * <p>Everything here runs on the server thread. The ledger is the only state; this class holds the
 * rules of opening, trading and settling a question, and the house that opens some on its own.
 */
public final class PredictionMarkets {
    /** Advancements a question may ask about, each with its vanilla identifier. */
    public static final List<String> ADVANCEMENTS = List.of(
            "minecraft:story/mine_diamond", "minecraft:story/shiny_gear", "minecraft:story/enter_the_nether",
            "minecraft:nether/find_fortress", "minecraft:nether/obtain_blaze_rod", "minecraft:nether/find_bastion",
            "minecraft:story/enter_the_end", "minecraft:end/kill_dragon", "minecraft:end/elytra",
            "minecraft:nether/summon_wither", "minecraft:nether/netherite_armor", "minecraft:story/cure_zombie_villager",
            "minecraft:adventure/hero_of_the_village", "minecraft:adventure/totem_of_undying",
            "minecraft:husbandry/tame_an_animal", "minecraft:adventure/sleep_in_bed");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CHECK_TICKS = 20;
    private static PredictionSetup setup;
    private static long nextHouseAt;

    private PredictionMarkets() {}

    public static void configure(PredictionSetup loaded) {
        setup = loaded;
        nextHouseAt = 0;
    }

    public static PredictionSetup setup() {
        if (setup == null) throw new IllegalStateException("Gambling Items configuration has not loaded");
        return setup;
    }

    public static PredictionSettings settings() { return setup().settings(); }

    private static long now() { return CasinoSecurity.now(); }

    // ---- Reading the players -------------------------------------------------------------------

    /** How many times this player did what a counted question counts, all time. */
    public static int count(ServerPlayer player, MarketKind kind) {
        var stats = player.getStats();
        return switch (kind) {
            case DEATHS -> stats.getValue(Stats.CUSTOM.get(Stats.DEATHS));
            case MOB_KILLS -> stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
            case DIAMONDS -> stats.getValue(Stats.BLOCK_MINED.get(Blocks.DIAMOND_ORE))
                    + stats.getValue(Stats.BLOCK_MINED.get(Blocks.DEEPSLATE_DIAMOND_ORE));
            default -> 0;
        };
    }

    public static boolean achieved(ServerPlayer player, String advancement) {
        //#if MC >= 1.20.2
        var holder = player.server.getAdvancements().get(ResourceLocation.parse(advancement));
        //#else
        //$ var holder = player.server.getAdvancements().getAdvancement(ResourceLocation.parse(advancement));
        //#endif
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** True when the game itself can see that the subject has done what the question asks. */
    private static boolean answered(ServerPlayer player, MarketRecord record) {
        if (record.kind() == MarketKind.ADVANCEMENT) return achieved(player, record.target());
        record.progress = Math.max(0, count(player, record.kind()) - record.baseline());
        return record.progress >= record.threshold();
    }

    // ---- Opening -------------------------------------------------------------------------------

    /** The outcome of a request to open a question: the question, or why it was refused. */
    public record Opening(MarketRecord record, String refusal) {
        static Opening refused(String reason) { return new Opening(null, reason); }
    }

    /**
     * Checks a question before anything is taken: the reason it would be refused, or null when it
     * can be opened. The same check runs again when it is opened.
     */
    public static String check(MinecraftServer server, UUID creator, MarketKind kind, ServerPlayer subject,
                               String target, int threshold, String question, int minutes, long subsidy) {
        PredictionSettings settings = settings();
        if (creator != null && openBy(server, creator) >= settings.maximumOpen()) return "too_many";
        boolean allowed = false;
        for (int duration : PredictionSettings.DURATIONS) allowed |= duration == minutes;
        if (!allowed) return "duration";
        if (kind.aboutPlayer()) {
            if (subject == null) return "no_subject";
            if (kind == MarketKind.ADVANCEMENT) {
                if (!ADVANCEMENTS.contains(target)) return "no_target";
                if (achieved(subject, target)) return "already_done";
            } else if (threshold < 1 || threshold > kind.maximumThreshold()) return "threshold";
        } else {
            String text = clean(question);
            if (text.length() < PredictionSettings.QUESTION_MIN) return "no_question";
        }
        if (subsidy < (creator == null ? 1 : settings.minimumSubsidy())) return "subsidy";
        return null;
    }

    /** A written question keeps its words, without formatting codes and within its length. */
    public static String clean(String question) {
        if (question == null) return "";
        String text = question.replaceAll("§.", "").replaceAll("\\s+", " ").trim();
        return text.length() > PredictionSettings.QUESTION_MAX ? text.substring(0, PredictionSettings.QUESTION_MAX) : text;
    }

    /** Opens a question funded by this subsidy. A null creator is the house. */
    public static Opening open(MinecraftServer server, UUID creator, String creatorName, MarketKind kind,
                               ServerPlayer subject, String target, int threshold, String question, int minutes,
                               long subsidy) {
        String refusal = check(server, creator, kind, subject, target, threshold, question, minutes, subsidy);
        if (refusal != null) return Opening.refused(refusal);
        PredictionSettings settings = settings();
        CasinoLedger ledger = CasinoLedger.get(server);
        long now = now();
        MarketRecord record = new MarketRecord(ledger.nextMarketId(), kind, creator, creatorName,
                subject == null ? null : subject.getUUID(), subject == null ? "" : subject.getGameProfile().getName(),
                kind == MarketKind.ADVANCEMENT ? target : "", kind.counted() ? threshold : 0,
                kind.counted() ? count(subject, kind) : 0, kind == MarketKind.CUSTOM ? clean(question) : "",
                now, now + minutes * 60_000L,
                PredictionMarket.open(subsidy, settings.shareValue(), settings.feeBasisPoints()));
        ledger.markets().add(record);
        ledger.setDirty();
        announce(server, Component.translatable("gui.gamblingitems.market.announce.opened", record.id(),
                MarketView.question(record), minutes(minutes)).withStyle(ChatFormatting.GOLD));
        return new Opening(record, null);
    }

    private static Component minutes(int minutes) {
        if (minutes % 1_440 == 0) return Component.translatable("gui.gamblingitems.market.days", minutes / 1_440);
        if (minutes % 60 == 0) return Component.translatable("gui.gamblingitems.market.hours", minutes / 60);
        return Component.translatable("gui.gamblingitems.market.minutes", minutes);
    }

    /** Unfinished questions this player opened. */
    public static int openBy(MinecraftServer server, UUID creator) {
        int open = 0;
        for (MarketRecord record : CasinoLedger.get(server).markets()) {
            if (creator.equals(record.creator()) && !record.market().status().finished()) open++;
        }
        return open;
    }

    // ---- Trading -------------------------------------------------------------------------------

    public static MarketRecord find(MinecraftServer server, int id) { return CasinoLedger.get(server).market(id); }

    /** Buys a side of an open question with this spend; null when nothing could be bought. */
    public static PredictionMarket.Purchase buy(MinecraftServer server, MarketRecord record, Side side, long spend) {
        if (record == null || !record.market().trading() || now() >= record.closesAt()) return null;
        PredictionMarket.Purchase purchase = record.market().buy(side, spend);
        if (purchase != null) CasinoLedger.get(server).setDirty();
        return purchase;
    }

    /** What a ticket is worth right now, and whether it can be cashed. */
    public record Quote(int state, long value) {
        public static final int UNKNOWN = 0, SELL = 1, REDEEM = 2, WORTHLESS = 3, WAITING = 4;
    }

    public static Quote quote(MinecraftServer server, Tickets.Ticket ticket) {
        if (ticket == null) return new Quote(Quote.UNKNOWN, 0);
        MarketRecord record = find(server, ticket.market());
        if (record == null) return new Quote(Quote.UNKNOWN, 0);
        PredictionMarket market = record.market();
        if (market.shares(ticket.side()) < ticket.shares()) return new Quote(Quote.UNKNOWN, 0);
        if (market.trading()) return new Quote(Quote.SELL, market.quoteSell(ticket.side(), ticket.shares()));
        if (!market.status().finished()) return new Quote(Quote.WAITING, 0);
        long value = market.payout(ticket.side(), ticket.shares());
        return new Quote(value > 0 ? Quote.REDEEM : Quote.WORTHLESS, value);
    }

    /** What cashing a ticket did: what the holder is paid, and whether the commission seized it. */
    public record Cashing(boolean done, long value, boolean seized) {
        static final Cashing REFUSED = new Cashing(false, 0, false);
    }

    /**
     * Sells a ticket back while its question trades, or cashes it once the question is settled. A
     * winning bet its own subject placed against themselves is looked at by the commission first.
     */
    public static Cashing cash(MinecraftServer server, Tickets.Ticket ticket) {
        Quote quote = quote(server, ticket);
        MarketRecord record = ticket == null ? null : find(server, ticket.market());
        if (record == null) return Cashing.REFUSED;
        PredictionMarket market = record.market();
        CasinoLedger ledger = CasinoLedger.get(server);
        switch (quote.state()) {
            case Quote.SELL -> {
                long value = market.sell(ticket.side(), ticket.shares());
                ledger.setDirty();
                return new Cashing(true, value, false);
            }
            case Quote.REDEEM, Quote.WORTHLESS -> {
                long value = market.redeem(ticket.side(), ticket.shares());
                ledger.setDirty();
                boolean fixed = value > 0 && market.status() == Status.RESOLVED && record.kind().sabotage() == ticket.side()
                        && ticket.buyer().equals(record.subject());
                if (fixed && CasinoSecurity.investigate(server, ticket.buyer(), record.subjectName(),
                        settings().insiderChance(), settings().insiderHeat())) {
                    return new Cashing(true, 0, true);
                }
                return new Cashing(true, value, false);
            }
            default -> {
                return Cashing.REFUSED;
            }
        }
    }

    // ---- Settling ------------------------------------------------------------------------------

    /** The creator states the answer of a written question; it can be disputed for a while. */
    public static boolean propose(MinecraftServer server, ServerPlayer player, MarketRecord record, Side answer) {
        if (record == null || record.kind() != MarketKind.CUSTOM || !player.getUUID().equals(record.creator())) return false;
        Status status = record.market().status();
        if (status != Status.OPEN && status != Status.CLOSED) return false;
        record.market().propose(answer, now(), settings().challengeMillis());
        CasinoLedger.get(server).setDirty();
        announce(server, Component.translatable("gui.gamblingitems.market.announce.proposed", record.id(),
                MarketView.question(record), MarketView.side(answer),
                Math.max(1, settings().challengeMillis() / 60_000)).withStyle(ChatFormatting.YELLOW));
        return true;
    }

    /** Anyone but the creator may dispute a stated answer; an operator then decides. */
    public static boolean dispute(MinecraftServer server, ServerPlayer player, MarketRecord record) {
        if (record == null || record.market().status() != Status.PROPOSED
                || player.getUUID().equals(record.creator())) return false;
        record.market().dispute();
        CasinoLedger.get(server).setDirty();
        announce(server, Component.translatable("gui.gamblingitems.market.announce.disputed", record.id(),
                player.getDisplayName()).withStyle(ChatFormatting.RED));
        return true;
    }

    /** An operator's answer, final: yes, no, or null to call the question off. */
    public static boolean judge(MinecraftServer server, MarketRecord record, Side answer) {
        if (record == null || record.market().status().finished()) return false;
        if (answer == null) finish(server, record, record.market().cancel());
        else finish(server, record, record.market().resolve(answer));
        return true;
    }

    /** What the pool and the fees leave goes to the creator; the house keeps its own. */
    private static void finish(MinecraftServer server, MarketRecord record, long left) {
        CasinoLedger ledger = CasinoLedger.get(server);
        long fees = record.market().takeFees();
        if (record.creator() != null) ledger.addCredit(record.creator(), left + fees);
        record.finishedAt = now();
        ledger.setDirty();
        PredictionMarket market = record.market();
        Component result = market.status() == Status.CANCELLED
                ? Component.translatable("gui.gamblingitems.market.announce.cancelled", record.id(), MarketView.question(record))
                : Component.translatable("gui.gamblingitems.market.announce.resolved", record.id(),
                        MarketView.question(record), MarketView.side(market.outcome()));
        announce(server, result.copy().withStyle(ChatFormatting.AQUA));
    }

    private static void announce(MinecraftServer server, Component text) {
        if (settings().announce()) server.getPlayerList().broadcastSystemMessage(text, false);
    }

    public static void tick(MinecraftServer server) {
        if (setup == null || server.getTickCount() % CHECK_TICKS != 0) return;
        check(server);
    }

    /** Looks at every unfinished question once: settles what the game can see, forgets what is old. */
    static void check(MinecraftServer server) {
        long now = now();
        CasinoLedger ledger = CasinoLedger.get(server);
        for (Iterator<MarketRecord> iterator = ledger.markets().iterator(); iterator.hasNext(); ) {
            MarketRecord record = iterator.next();
            PredictionMarket market = record.market();
            if (market.status().finished()) {
                // A question is kept long enough for its tickets to be cashed, then forgotten.
                if (now - record.finishedAt > settings().retentionMillis()) {
                    iterator.remove();
                    ledger.setDirty();
                }
                continue;
            }
            settle(server, record, now);
        }
        house(server, now);
    }

    private static void settle(MinecraftServer server, MarketRecord record, long now) {
        PredictionMarket market = record.market();
        switch (market.status()) {
            case OPEN -> {
                if (record.kind().aboutPlayer()) {
                    ServerPlayer subject = server.getPlayerList().getPlayer(record.subject());
                    int before = record.progress;
                    if (subject != null && answered(subject, record)) {
                        finish(server, record, market.resolve(Side.YES));
                        return;
                    }
                    if (before != record.progress) CasinoLedger.get(server).setDirty();
                    if (now >= record.closesAt()) finish(server, record, market.resolve(Side.NO));
                } else if (now >= record.closesAt()) {
                    market.close();
                    CasinoLedger.get(server).setDirty();
                    ServerPlayer creator = server.getPlayerList().getPlayer(record.creator());
                    if (creator != null) dev.gamblingitems.fabric.Compat.message(creator, Component.translatable(
                            "gui.gamblingitems.market.answer_now", record.id()).withStyle(ChatFormatting.YELLOW), false);
                }
            }
            case CLOSED -> {
                // A creator who never answers loses the question: it is called off and every ticket repaid.
                if (now >= record.closesAt() + settings().proposalMillis()) finish(server, record, market.cancel());
            }
            case PROPOSED -> {
                long left = market.finishChallenge(now);
                if (left >= 0) finish(server, record, left);
            }
            case DISPUTED -> {
                if (now >= market.challengeEnds() + settings().disputeMillis()) finish(server, record, market.cancel());
            }
            default -> { }
        }
    }

    /** Now and then the house opens a question about someone who is playing. */
    private static void house(MinecraftServer server, long now) {
        PredictionSettings settings = settings();
        if (!settings.houseMarkets() || settings.houseMaximumOpen() == 0) return;
        if (nextHouseAt == 0) nextHouseAt = now + Math.min(settings.houseIntervalMillis(), 120_000L);
        if (now < nextHouseAt) return;
        nextHouseAt = now + settings.houseIntervalMillis();
        int open = 0;
        for (MarketRecord record : CasinoLedger.get(server).markets()) {
            if (record.house() && !record.market().status().finished()) open++;
        }
        if (open >= settings.houseMaximumOpen()) return;
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) if (!player.isSpectator()) players.add(player);
        if (players.isEmpty()) return;
        ServerPlayer subject = players.get(RANDOM.nextInt(players.size()));
        int minutes = (int) Math.max(1, settings.houseDurationMillis() / 60_000);
        // The house trades on its own clock, which need not be one a player could choose.
        int duration = PredictionSettings.DURATIONS[0];
        for (int choice : PredictionSettings.DURATIONS) if (Math.abs(choice - minutes) < Math.abs(duration - minutes)) duration = choice;
        MarketKind kind = MarketKind.values()[RANDOM.nextInt(4)];
        String target = "";
        int threshold = 0;
        if (kind == MarketKind.ADVANCEMENT) {
            List<String> left = new ArrayList<>();
            for (String advancement : ADVANCEMENTS) if (!achieved(subject, advancement)) left.add(advancement);
            if (left.isEmpty()) kind = MarketKind.MOB_KILLS;
            else target = left.get(RANDOM.nextInt(left.size()));
        }
        switch (kind) {
            case DEATHS -> threshold = 1 + RANDOM.nextInt(3);
            case DIAMONDS -> threshold = 3 + RANDOM.nextInt(8);
            case MOB_KILLS -> threshold = 10 + RANDOM.nextInt(21);
            default -> { }
        }
        open(server, null, "", kind, subject, target, threshold, "", duration, settings.houseSubsidy());
    }

    // ---- Listing -------------------------------------------------------------------------------

    /** The questions in the order the screens list them: trading first, the busiest on top. */
    public static List<MarketRecord> listed(MinecraftServer server) {
        List<MarketRecord> list = new ArrayList<>(CasinoLedger.get(server).markets());
        list.sort(Comparator.comparingInt((MarketRecord record) -> rank(record.market().status()))
                .thenComparing(Comparator.comparingLong((MarketRecord record) -> record.market().volume()).reversed())
                .thenComparing(Comparator.comparingInt(MarketRecord::id).reversed()));
        return list;
    }

    private static int rank(Status status) {
        return switch (status) {
            case OPEN -> 0;
            case PROPOSED, DISPUTED, CLOSED -> 1;
            default -> 2;
        };
    }
}
