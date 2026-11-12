package dev.gamblingitems.fabric.prediction;

import dev.gamblingitems.core.prediction.PredictionMarket;
import dev.gamblingitems.core.prediction.PredictionMarket.Side;
import dev.gamblingitems.core.prediction.PredictionMarket.Status;
import dev.gamblingitems.fabric.Nbt;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * A question as a screen or a board shows it: what it asks, its price, its state and its clock. It is
 * written by the server into the data of a display item or of a board, which Minecraft already
 * synchronises, and read back on the client. It is a description, never a way to trade.
 */
public record MarketView(int id, MarketKind kind, String subject, String target, int threshold, String question,
                         String creator, boolean house, Status status, Side outcome, Side proposal, int price,
                         long volume, long pool, long closesIn, long challengeIn, int progress, boolean mine,
                         boolean aboutViewer) {
    public static final String KEY = "gamblingitems_market";

    public static MarketView of(MarketRecord record, UUID viewer, long now) {
        PredictionMarket market = record.market();
        return new MarketView(record.id(), record.kind(), record.subjectName(), record.target(), record.threshold(),
                record.question(), record.creatorName(), record.house(), market.status(), market.outcome(),
                market.proposal(), market.priceBasisPoints(), market.volume(), market.pool(),
                Math.max(0, record.closesAt() - now), Math.max(0, market.challengeEnds() - now), record.progress,
                viewer != null && viewer.equals(record.creator()), viewer != null && viewer.equals(record.subject()));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("kind", kind.id());
        tag.putString("subject", subject);
        tag.putString("target", target);
        tag.putInt("threshold", threshold);
        tag.putString("question", question);
        tag.putString("creator", creator);
        tag.putBoolean("house", house);
        tag.putString("status", status.id());
        tag.putString("outcome", outcome == null ? "" : outcome.id());
        tag.putString("proposal", proposal == null ? "" : proposal.id());
        tag.putInt("price", price);
        tag.putLong("volume", volume);
        tag.putLong("pool", pool);
        tag.putLong("closesIn", closesIn);
        tag.putLong("challengeIn", challengeIn);
        tag.putInt("progress", progress);
        tag.putBoolean("mine", mine);
        tag.putBoolean("aboutViewer", aboutViewer);
        return tag;
    }

    /** Null when the tag does not describe a question. */
    public static MarketView load(CompoundTag tag) {
        if (Nbt.integer(tag, "id", 0) <= 0) return null;
        try {
            String outcome = Nbt.string(tag, "outcome"), proposal = Nbt.string(tag, "proposal");
            return new MarketView(Nbt.integer(tag, "id", 0), MarketKind.fromId(Nbt.string(tag, "kind")),
                    Nbt.string(tag, "subject"), Nbt.string(tag, "target"), Nbt.integer(tag, "threshold", 0),
                    Nbt.string(tag, "question"), Nbt.string(tag, "creator"), Nbt.bool(tag, "house"),
                    Status.fromId(Nbt.string(tag, "status")), outcome.isEmpty() ? null : Side.fromId(outcome),
                    proposal.isEmpty() ? null : Side.fromId(proposal), Nbt.integer(tag, "price", 5_000),
                    Nbt.longValue(tag, "volume"), Nbt.longValue(tag, "pool"), Nbt.longValue(tag, "closesIn"),
                    Nbt.longValue(tag, "challengeIn"), Nbt.integer(tag, "progress", 0), Nbt.bool(tag, "mine"),
                    Nbt.bool(tag, "aboutViewer"));
        } catch (IllegalArgumentException corrupt) {
            return null;
        }
    }

    public Component text() { return question(kind, subject, target, threshold, question); }

    /** The question in the language of whoever reads it. A written question stays as it was written. */
    public static Component question(MarketKind kind, String subject, String target, int threshold, String question) {
        return switch (kind) {
            case ADVANCEMENT -> Component.translatable("gui.gamblingitems.market.q.advancement", subject, advancement(target));
            case DEATHS, DIAMONDS, MOB_KILLS ->
                    Component.translatable("gui.gamblingitems.market.q." + kind.id(), subject, threshold);
            case CUSTOM -> Component.literal(question);
        };
    }

    public static Component question(MarketRecord record) {
        return question(record.kind(), record.subjectName(), record.target(), record.threshold(), record.question());
    }

    /** The title of a vanilla advancement, as its own translation spells it. */
    public static Component advancement(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return Component.translatable("advancements." + path.replace('/', '.') + ".title");
    }

    public static Component side(Side side) {
        return Component.translatable("gui.gamblingitems.market.side." + side.id());
    }
}
