package dev.gamblingitems.fabric.prediction;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server tuning of the prediction market. Durations are milliseconds of wall-clock time, so a
 * question keeps its deadline across restarts.
 *
 * @param shareValue          what one winning share pays, in item values
 * @param feeBasisPoints      the part of every purchase kept for the creator of the question
 * @param minimumSubsidy      the smallest deposit that opens a question
 * @param maximumOpen         how many unfinished questions one player may have opened at once
 * @param challengeMillis     how long a stated answer stays open to dispute
 * @param proposalMillis      how long a creator has to state the answer once trading closes
 * @param disputeMillis       how long a disputed question waits for an operator before it is called off
 * @param retentionMillis     how long a finished question is kept, so its tickets can be cashed
 * @param announce            whether new and settled questions are announced to everyone
 * @param insiderChance       how likely the commission spots a bet against oneself, in basis points
 * @param insiderHeat         the suspicion such a bet leaves when it goes unnoticed
 * @param houseMarkets        whether the house opens questions about the players on its own
 * @param houseIntervalMillis how often the house considers opening one
 * @param houseMaximumOpen    how many of its own questions the house keeps open at once
 * @param houseSubsidy        what the house deposits into each of them
 * @param houseDurationMillis how long its questions trade
 */
public record PredictionSettings(long shareValue, int feeBasisPoints, long minimumSubsidy, int maximumOpen,
                                 long challengeMillis, long proposalMillis, long disputeMillis, long retentionMillis,
                                 boolean announce, int insiderChance, int insiderHeat, boolean houseMarkets,
                                 long houseIntervalMillis, int houseMaximumOpen, long houseSubsidy,
                                 long houseDurationMillis) {
    /** Slots of the prediction vault: the stake, the ticket being cashed, the written question, the winnings. */
    public static final int STAKE_SLOTS = 4;
    public static final int INPUT_SLOT = 0;
    public static final int TICKET_SLOT = INPUT_SLOT + STAKE_SLOTS;
    public static final int QUESTION_SLOT = TICKET_SLOT + 1;
    public static final int FIRST_PAYOUT_SLOT = QUESTION_SLOT + 1;
    public static final int PAYOUT_SLOTS = 27;
    public static final int VAULT_SIZE = FIRST_PAYOUT_SLOT + PAYOUT_SLOTS;
    /** The durations a player may give a question, in minutes. */
    public static final int[] DURATIONS = {10, 30, 60, 180, 720, 1_440, 4_320, 10_080};
    public static final int QUESTION_MIN = 4, QUESTION_MAX = 90;

    public PredictionSettings {
        if (shareValue < 1 || shareValue > 1_000_000_000L || feeBasisPoints < 0 || feeBasisPoints > 2_000
                || minimumSubsidy < 1 || minimumSubsidy > 1_000_000_000L || maximumOpen < 1 || maximumOpen > 100
                || challengeMillis < 0 || proposalMillis < 60_000 || disputeMillis < 60_000 || retentionMillis < 60_000
                || insiderChance < 0 || insiderChance > 10_000 || insiderHeat < 0 || insiderHeat > 100
                || houseIntervalMillis < 60_000 || houseMaximumOpen < 0 || houseMaximumOpen > 20
                || houseSubsidy < 1 || houseSubsidy > 1_000_000_000L || houseDurationMillis < 60_000) {
            throw new IllegalArgumentException("Invalid prediction market settings");
        }
    }

    public static PredictionSettings defaults() {
        return new PredictionSettings(1_000, 200, 5_000, 5, 10 * 60_000L, 60 * 60_000L, 24 * 3_600_000L,
                14 * 86_400_000L, true, 6_000, 30, true, 20 * 60_000L, 3, 10_000, 45 * 60_000L);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, PredictionSettings> CODEC = new StreamCodec<>() {
        @Override public PredictionSettings decode(RegistryFriendlyByteBuf buffer) {
            return new PredictionSettings(buffer.readVarLong(), buffer.readVarInt(), buffer.readVarLong(),
                    buffer.readVarInt(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(),
                    buffer.readVarLong(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readVarLong(), buffer.readVarInt(), buffer.readVarLong(),
                    buffer.readVarLong());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, PredictionSettings data) {
            buffer.writeVarLong(data.shareValue());
            buffer.writeVarInt(data.feeBasisPoints());
            buffer.writeVarLong(data.minimumSubsidy());
            buffer.writeVarInt(data.maximumOpen());
            buffer.writeVarLong(data.challengeMillis());
            buffer.writeVarLong(data.proposalMillis());
            buffer.writeVarLong(data.disputeMillis());
            buffer.writeVarLong(data.retentionMillis());
            buffer.writeBoolean(data.announce());
            buffer.writeVarInt(data.insiderChance());
            buffer.writeVarInt(data.insiderHeat());
            buffer.writeBoolean(data.houseMarkets());
            buffer.writeVarLong(data.houseIntervalMillis());
            buffer.writeVarInt(data.houseMaximumOpen());
            buffer.writeVarLong(data.houseSubsidy());
            buffer.writeVarLong(data.houseDurationMillis());
        }
    };
}
