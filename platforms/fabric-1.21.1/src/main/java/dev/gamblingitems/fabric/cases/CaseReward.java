package dev.gamblingitems.fabric.cases;

import dev.gamblingitems.core.cases.CaseRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** One line of a case table: what is won, how many, and its weight in parts per million. */
public record CaseReward(ResourceLocation item, int count, int weight) {
    public CaseReward {
        if (count < 1 || count > 64 || weight < 1 || weight > CaseRules.TOTAL_WEIGHT) {
            throw new IllegalArgumentException("Invalid case reward");
        }
    }

    public ItemStack stack() { return new ItemStack(BuiltInRegistries.ITEM.get(item), count); }

    public static final StreamCodec<RegistryFriendlyByteBuf, CaseReward> CODEC = new StreamCodec<>() {
        @Override public CaseReward decode(RegistryFriendlyByteBuf buffer) {
            return new CaseReward(buffer.readResourceLocation(), buffer.readVarInt(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, CaseReward data) {
            buffer.writeResourceLocation(data.item());
            buffer.writeVarInt(data.count());
            buffer.writeVarInt(data.weight());
        }
    };
}
