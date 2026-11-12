package dev.gamblingitems.fabric.machines;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.MachineTable;
import java.math.BigDecimal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server tuning of one prize machine: whether it is open, the bets it takes, the share it gives
 * back and the pace of a play, with the table of its prizes.
 *
 * <p>A bet is any priced item, in whatever quantity, as at a slot machine. A maximum of zero means
 * no maximum other than what the player could be paid.
 */
public record MachineSettings(boolean enabled, long minimumStake, long maximumStake, int returnBasisPoints,
                              int playTicks, int resultTicks, MachineTable table) {
    /** Slots of the machine vault: the credit being prepared, then the winnings. */
    public static final int STAKE_SLOTS = 5;
    public static final int INPUT_SLOT = 0;
    public static final int FIRST_PAYOUT_SLOT = INPUT_SLOT + STAKE_SLOTS;
    public static final int PAYOUT_SLOTS = 54;
    public static final int VAULT_SIZE = FIRST_PAYOUT_SLOT + PAYOUT_SLOTS;

    public MachineSettings {
        if (table == null) throw new IllegalArgumentException("A machine needs its table of prizes");
        String name = table.mode().id();
        if (minimumStake < 1 || minimumStake > 1_000_000_000L) {
            throw new IllegalArgumentException(name + ": minimumStake must be between 1 and 1000000000, not " + minimumStake);
        }
        if (maximumStake != 0 && (maximumStake < minimumStake || maximumStake > 1_000_000_000L)) {
            throw new IllegalArgumentException(name + ": maximumStake must be 0 (no maximum) or between minimumStake and 1000000000, not " + maximumStake);
        }
        if (returnBasisPoints < 1 || returnBasisPoints > 10_000) {
            throw new IllegalArgumentException(name + ": returnBasisPoints must be between 1 and 10000, not " + returnBasisPoints);
        }
        if (playTicks < 10 || playTicks > 400 || resultTicks < 10 || resultTicks > 400) {
            throw new IllegalArgumentException(name + ": playTicks and resultTicks must be between 10 and 400");
        }
        // A machine may be generous, never give back more than it takes on average.
        BigDecimal rate = BigDecimal.valueOf(returnBasisPoints, 4);
        for (int choice = 0; choice < table.choices(); choice++) {
            BigDecimal effective = table.returnToPlayer(choice).multiply(rate);
            if (effective.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException(name + ": its prizes give back " + effective.movePointRight(2).stripTrailingZeros().toPlainString()
                        + "% of the stakes" + (table.choices() > 1 ? " at choice " + (choice + 1) : "") + ", above 100%");
            }
        }
    }

    public GameMode mode() { return table.mode(); }
    public BigDecimal returnRate() { return BigDecimal.valueOf(returnBasisPoints, 4); }

    /** The most this machine could ever owe for that bet, which the player must be able to hold. */
    public long maximumPayout(long stake) {
        return MachineTable.payout(stake, table.bestMultiplier(), returnRate());
    }

    /** True when this credit may be played: enough of it, and not above the maximum if there is one. */
    public boolean accepts(long stake) {
        return stake >= minimumStake && (maximumStake == 0 || stake <= maximumStake);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineSettings> CODEC = new StreamCodec<>() {
        @Override public MachineSettings decode(RegistryFriendlyByteBuf buffer) {
            boolean enabled = buffer.readBoolean();
            long minimum = buffer.readVarLong(), maximum = buffer.readVarLong();
            int rate = buffer.readVarInt(), play = buffer.readVarInt(), result = buffer.readVarInt();
            GameMode mode = GameMode.fromId(buffer.readUtf(32));
            return new MachineSettings(enabled, minimum, maximum, rate, play, result,
                    new MachineTable(mode, ints(buffer), ints(buffer)));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, MachineSettings data) {
            buffer.writeBoolean(data.enabled());
            buffer.writeVarLong(data.minimumStake());
            buffer.writeVarLong(data.maximumStake());
            buffer.writeVarInt(data.returnBasisPoints());
            buffer.writeVarInt(data.playTicks());
            buffer.writeVarInt(data.resultTicks());
            buffer.writeUtf(data.mode().id(), 32);
            writeInts(buffer, data.table().multipliers());
            writeInts(buffer, data.table().weights());
        }
    };

    private static int[] ints(RegistryFriendlyByteBuf buffer) {
        int length = buffer.readVarInt();
        if (length < 0 || length > MachineTable.MAX_ENTRIES) throw new IllegalArgumentException("Invalid machine table length " + length);
        int[] values = new int[length];
        for (int index = 0; index < length; index++) values[index] = buffer.readVarInt();
        return values;
    }

    private static void writeInts(RegistryFriendlyByteBuf buffer, int[] values) {
        buffer.writeVarInt(values.length);
        for (int value : values) buffer.writeVarInt(value);
    }
}
