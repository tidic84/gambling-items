package dev.gamblingitems.fabric.roulette;

import dev.gamblingitems.core.roulette.RouletteRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Server tuning of a roulette table: the wheel, what each colour pays and the length of each phase.
 * A round keeps the settings it started with, so a reload never changes a spin in progress.
 */
public record RouletteSettings(ResourceLocation stakeItem, int minimumStake,
                               int redSlots, int blackSlots, int greenSlots,
                               int colourPayout, int greenPayout,
                               int bettingTicks, int spinTicks, int resultTicks) {
    /** Slots of the roulette vault: the bet being prepared, the engaged bet, then the winnings. */
    public static final int INPUT_SLOT = 0, ENGAGED_SLOT = 1, FIRST_PAYOUT_SLOT = 2, PAYOUT_SLOTS = 54;
    public static final int VAULT_SIZE = FIRST_PAYOUT_SLOT + PAYOUT_SLOTS;

    public RouletteSettings {
        if (minimumStake < 1 || minimumStake > 64
                || bettingTicks < 40 || bettingTicks > 1_200
                || spinTicks < 20 || spinTicks > 400
                || resultTicks < 20 || resultTicks > 400) {
            throw new IllegalArgumentException("Invalid roulette settings");
        }
    }

    public RouletteRules rules() {
        return new RouletteRules(redSlots, blackSlots, greenSlots, colourPayout, greenPayout);
    }

    public ItemStack stakeStack(int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(stakeItem), count);
    }

    public boolean isStake(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, stakeStack(1));
    }

    public int stackLimit() { return stakeStack(1).getMaxStackSize(); }

    public long winningsCapacity() { return (long) PAYOUT_SLOTS * stackLimit(); }

    /** The largest bet this room could still be paid for, at the best paying colour. */
    public int largestStake(long freeWinningSpace) {
        long affordable = freeWinningSpace / Math.max(colourPayout, greenPayout);
        return (int) Math.max(0, Math.min(stackLimit(), affordable));
    }

    /** A table whose smallest bet could not be paid at all is refused before anyone plays. */
    public boolean isPayable() {
        rules();
        return largestStake(winningsCapacity()) >= minimumStake;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RouletteSettings> CODEC = new StreamCodec<>() {
        @Override public RouletteSettings decode(RegistryFriendlyByteBuf buffer) {
            return new RouletteSettings(buffer.readResourceLocation(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, RouletteSettings data) {
            buffer.writeResourceLocation(data.stakeItem());
            buffer.writeVarInt(data.minimumStake());
            buffer.writeVarInt(data.redSlots());
            buffer.writeVarInt(data.blackSlots());
            buffer.writeVarInt(data.greenSlots());
            buffer.writeVarInt(data.colourPayout());
            buffer.writeVarInt(data.greenPayout());
            buffer.writeVarInt(data.bettingTicks());
            buffer.writeVarInt(data.spinTicks());
            buffer.writeVarInt(data.resultTicks());
        }
    };
}
