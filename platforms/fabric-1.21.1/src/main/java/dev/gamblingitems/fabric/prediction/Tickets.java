package dev.gamblingitems.fabric.prediction;

import dev.gamblingitems.core.prediction.PredictionMarket;
import dev.gamblingitems.core.prediction.PredictionMarket.Side;
import dev.gamblingitems.fabric.Compat;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.Nbt;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A position on a question is a physical ticket: it can be given, sold, stored in a chest, lost on
 * death or stolen, and it pays whoever cashes it. It still remembers who bought it, which is what the
 * gaming commission looks at when someone bet against themselves.
 */
public final class Tickets {
    public static final String KEY = "gamblingitems_ticket";

    public record Ticket(int market, Side side, long shares, long paid, UUID buyer) {}

    private Tickets() {}

    public static ItemStack create(MarketRecord record, PredictionMarket.Purchase purchase, UUID buyer) {
        ItemStack stack = new ItemStack(ModContent.PREDICTION_TICKET);
        CompoundTag tag = new CompoundTag();
        tag.putInt("market", record.id());
        tag.putString("side", purchase.side().id());
        tag.putLong("shares", purchase.shares());
        tag.putLong("paid", purchase.net() + purchase.fee());
        Nbt.putUuid(tag, "buyer", buyer);
        Compat.setItemData(stack, KEY, tag);
        Compat.name(stack, Component.translatable("item.gamblingitems.prediction_ticket.of",
                MarketView.side(purchase.side()), record.id()).withStyle(purchase.side().yes()
                        ? ChatFormatting.GREEN : ChatFormatting.RED));
        Compat.lore(stack, List.of(
                MarketView.question(record).copy().withStyle(ChatFormatting.WHITE),
                Component.translatable("item.gamblingitems.prediction_ticket.shares", shares(purchase.shares()))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("item.gamblingitems.prediction_ticket.paid", value(purchase.net() + purchase.fee()))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("item.gamblingitems.prediction_ticket.bearer").withStyle(ChatFormatting.DARK_GRAY)));
        return stack;
    }

    /** The position a stack carries, or null when it is not a ticket of this mod. */
    public static Ticket read(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ModContent.PREDICTION_TICKET)) return null;
        CompoundTag tag = Compat.itemData(stack, KEY);
        int market = Nbt.integer(tag, "market", 0);
        long shares = Nbt.longValue(tag, "shares");
        if (market <= 0 || shares <= 0 || !tag.contains("buyer")) return null;
        try {
            return new Ticket(market, Side.fromId(Nbt.string(tag, "side")), shares, Nbt.longValue(tag, "paid"),
                    Nbt.uuid(tag, "buyer"));
        } catch (IllegalArgumentException corrupt) {
            return null;
        }
    }

    /** Shares are stored in thousandths and read with up to three decimals. */
    public static String shares(long milli) {
        return BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString();
    }

    public static String value(long value) {
        return BigDecimal.valueOf(value, 3).stripTrailingZeros().toPlainString();
    }
}
