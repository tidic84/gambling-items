package dev.gamblingitems.fabric.arcade;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * How the arcade pays: in tickets, each worth {@code ticketValue} of item value, spent at the
 * cashier's prize counter. The cashier also changes items into casino chips, each chip of 1 worth
 * {@code chipValue}.
 *
 * <p>With {@code tickets} off, the cabinets pay in items, as they did before the arcade.
 */
public record ArcadeSettings(boolean tickets, long ticketValue, long chipValue, int raceBettingTicks, List<Prize> prizes) {
    /** The chips the cashier hands out, in units of {@code chipValue}. */
    public static final int[] CHIPS = {1, 5, 25, 100};
    public static final int MAX_PRIZES = 32;

    /** One shelf of the prize counter: so many of an item for so many tickets. */
    public record Prize(ResourceLocation item, int count, long tickets) {
        public Prize {
            if (count < 1 || count > 64) throw new IllegalArgumentException("A prize is between 1 and 64 items, not " + count);
            if (tickets < 1 || tickets > 1_000_000_000_000L) throw new IllegalArgumentException("A prize costs between 1 and 10^12 tickets, not " + tickets);
        }
    }

    public ArcadeSettings {
        if (ticketValue < 1 || ticketValue > 1_000_000) throw new IllegalArgumentException("arcade.ticketValue must be between 1 and 1000000, not " + ticketValue);
        if (chipValue < 1 || chipValue > 10_000_000) throw new IllegalArgumentException("arcade.chipValue must be between 1 and 10000000, not " + chipValue);
        if (raceBettingTicks < 40 || raceBettingTicks > 1200) throw new IllegalArgumentException("arcade.raceBettingTicks must be between 40 and 1200, not " + raceBettingTicks);
        if (prizes.size() > MAX_PRIZES) throw new IllegalArgumentException("arcade.prizes has at most " + MAX_PRIZES + " prizes");
        prizes = List.copyOf(prizes);
    }

    /** The tickets a payout of this value is worth, rounded down as a payout in items is. */
    public long ticketsFor(long paid) { return paid <= 0 ? 0 : paid / ticketValue; }

    public static final StreamCodec<RegistryFriendlyByteBuf, ArcadeSettings> CODEC = new StreamCodec<>() {
        @Override public ArcadeSettings decode(RegistryFriendlyByteBuf buffer) {
            boolean tickets = buffer.readBoolean();
            long ticketValue = buffer.readVarLong(), chipValue = buffer.readVarLong();
            int betting = buffer.readVarInt(), count = buffer.readVarInt();
            if (count < 0 || count > MAX_PRIZES) throw new IllegalArgumentException("Invalid prize count " + count);
            List<Prize> prizes = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                prizes.add(new Prize(ResourceLocation.parse(buffer.readUtf(256)), buffer.readVarInt(), buffer.readVarLong()));
            }
            return new ArcadeSettings(tickets, ticketValue, chipValue, betting, prizes);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, ArcadeSettings data) {
            buffer.writeBoolean(data.tickets());
            buffer.writeVarLong(data.ticketValue());
            buffer.writeVarLong(data.chipValue());
            buffer.writeVarInt(data.raceBettingTicks());
            buffer.writeVarInt(data.prizes().size());
            for (Prize prize : data.prizes()) {
                buffer.writeUtf(prize.item().toString(), 256);
                buffer.writeVarInt(prize.count());
                buffer.writeVarLong(prize.tickets());
            }
        }
    };
}
