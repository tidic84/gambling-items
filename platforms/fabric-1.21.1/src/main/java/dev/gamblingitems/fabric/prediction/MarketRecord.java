package dev.gamblingitems.fabric.prediction;

import dev.gamblingitems.core.prediction.PredictionMarket;
import dev.gamblingitems.core.prediction.PredictionMarket.Side;
import dev.gamblingitems.core.prediction.PredictionMarket.Status;
import dev.gamblingitems.fabric.Nbt;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * One question of the prediction market as the server keeps it: what it asks, about whom, who opened
 * it, until when it trades, and its market. Times are wall-clock milliseconds.
 */
public final class MarketRecord {
    private final int id;
    private final MarketKind kind;
    private final UUID creator, subject;
    private final String creatorName, subjectName, target, question;
    private final int threshold, baseline;
    private final long createdAt, closesAt;
    private final PredictionMarket market;
    /** How far the subject has come since the question opened, for the screens. */
    public int progress;
    public long finishedAt;

    public MarketRecord(int id, MarketKind kind, UUID creator, String creatorName, UUID subject, String subjectName,
                        String target, int threshold, int baseline, String question, long createdAt, long closesAt,
                        PredictionMarket market) {
        this.id = id;
        this.kind = kind;
        this.creator = creator;
        this.creatorName = creatorName == null ? "" : creatorName;
        this.subject = subject;
        this.subjectName = subjectName == null ? "" : subjectName;
        this.target = target == null ? "" : target;
        this.threshold = threshold;
        this.baseline = baseline;
        this.question = question == null ? "" : question;
        this.createdAt = createdAt;
        this.closesAt = closesAt;
        this.market = market;
    }

    public int id() { return id; }
    public MarketKind kind() { return kind; }
    /** Null for a question the house opened itself. */
    public UUID creator() { return creator; }
    public String creatorName() { return creatorName; }
    public UUID subject() { return subject; }
    public String subjectName() { return subjectName; }
    /** The advancement asked about, for an advancement question. */
    public String target() { return target; }
    public int threshold() { return threshold; }
    public int baseline() { return baseline; }
    public String question() { return question; }
    public long createdAt() { return createdAt; }
    public long closesAt() { return closesAt; }
    public PredictionMarket market() { return market; }
    public boolean house() { return creator == null; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("kind", kind.id());
        Nbt.putOptionalUuid(tag, "creator", creator);
        tag.putString("creatorName", creatorName);
        Nbt.putOptionalUuid(tag, "subject", subject);
        tag.putString("subjectName", subjectName);
        tag.putString("target", target);
        tag.putInt("threshold", threshold);
        tag.putInt("baseline", baseline);
        tag.putInt("progress", progress);
        tag.putString("question", question);
        tag.putLong("createdAt", createdAt);
        tag.putLong("closesAt", closesAt);
        tag.putLong("finishedAt", finishedAt);
        tag.putLong("shareValue", market.shareValue());
        tag.putInt("fee", market.feeBasisPoints());
        tag.putDouble("liquidity", market.liquidity());
        tag.putLong("yes", market.yesShares());
        tag.putLong("no", market.noShares());
        tag.putLong("pool", market.pool());
        tag.putLong("fees", market.fees());
        tag.putLong("volume", market.volume());
        tag.putString("status", market.status().id());
        tag.putString("outcome", market.outcome() == null ? "" : market.outcome().id());
        tag.putString("proposal", market.proposal() == null ? "" : market.proposal().id());
        tag.putLong("challengeEnds", market.challengeEnds());
        tag.putInt("cancelPrice", market.cancelPrice());
        return tag;
    }

    public static MarketRecord load(CompoundTag tag) {
        String outcome = Nbt.string(tag, "outcome"), proposal = Nbt.string(tag, "proposal");
        PredictionMarket market = PredictionMarket.restore(Nbt.longValue(tag, "shareValue"), Nbt.integer(tag, "fee", 0),
                Nbt.doubleValue(tag, "liquidity"), Nbt.longValue(tag, "yes"), Nbt.longValue(tag, "no"),
                Nbt.longValue(tag, "pool"), Nbt.longValue(tag, "fees"), Nbt.longValue(tag, "volume"),
                Status.fromId(Nbt.string(tag, "status")), outcome.isEmpty() ? null : Side.fromId(outcome),
                proposal.isEmpty() ? null : Side.fromId(proposal), Nbt.longValue(tag, "challengeEnds"),
                Nbt.integer(tag, "cancelPrice", -1));
        MarketRecord record = new MarketRecord(Nbt.integer(tag, "id", 0), MarketKind.fromId(Nbt.string(tag, "kind")),
                Nbt.optionalUuid(tag, "creator"), Nbt.string(tag, "creatorName"), Nbt.optionalUuid(tag, "subject"),
                Nbt.string(tag, "subjectName"), Nbt.string(tag, "target"), Nbt.integer(tag, "threshold", 0),
                Nbt.integer(tag, "baseline", 0), Nbt.string(tag, "question"), Nbt.longValue(tag, "createdAt"),
                Nbt.longValue(tag, "closesAt"), market);
        record.progress = Nbt.integer(tag, "progress", 0);
        record.finishedAt = Nbt.longValue(tag, "finishedAt");
        return record;
    }
}
