package dev.gamblingitems.fabric.cases;

import dev.gamblingitems.core.cases.CaseRules;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * One configured case: a name, a price and a weighted reward table.
 * The record is immutable, so an opening keeps the price and the table it started with.
 */
public record CaseDefinition(String id, String name, ResourceLocation priceItem, int priceCount,
                             List<CaseReward> rewards) {
    public static final int MAX_NAME_LENGTH = 32;

    public CaseDefinition {
        rewards = List.copyOf(rewards);
        if (!id.matches("[a-z0-9_]{1,32}") || name.isBlank() || name.length() > MAX_NAME_LENGTH
                || priceCount < 1 || priceCount > 64
                || rewards.size() < CaseRules.MIN_REWARDS || rewards.size() > CaseRules.MAX_REWARDS) {
            throw new IllegalArgumentException("Invalid case: " + id);
        }
        long total = 0;
        for (CaseReward reward : rewards) total += reward.weight();
        if (total != CaseRules.TOTAL_WEIGHT) {
            throw new IllegalArgumentException("Case " + id + " does not cover its whole table");
        }
    }

    public ItemStack priceStack() { return new ItemStack(BuiltInRegistries.ITEM.get(priceItem), priceCount); }

    /**
     * How this case is called on a screen: translated when it is one of the cases this mod ships,
     * and otherwise exactly the name an administrator wrote in the configuration.
     */
    public net.minecraft.network.chat.Component title() {
        for (dev.gamblingitems.core.cases.CaseRarity rarity
                : dev.gamblingitems.core.cases.CaseRarity.values()) {
            if (rarity.caseId().equals(id)) {
                return net.minecraft.network.chat.Component.translatable(
                        "gui.gamblingitems.case." + rarity.id());
            }
        }
        return net.minecraft.network.chat.Component.literal(name);
    }

    /** True for a stack that pays for one opening; a partial stack is refused, never partly taken. */
    public boolean pays(ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() >= priceCount
                && ItemStack.isSameItemSameComponents(stack, new ItemStack(BuiltInRegistries.ITEM.get(priceItem)));
    }

    public int[] weightArray() {
        int[] weights = new int[rewards.size()];
        for (int index = 0; index < weights.length; index++) weights[index] = rewards.get(index).weight();
        return weights;
    }

    public BigDecimal percentOf(int index) { return CaseRules.percentOf(rewards.get(index).weight()); }

    public long priceValue(ValueCatalog catalog) { return catalog.valueOf(priceStack()); }

    public List<Long> rewardValues(ValueCatalog catalog) {
        List<Long> values = new ArrayList<>(rewards.size());
        for (CaseReward reward : rewards) values.add(catalog.valueOf(reward.stack()));
        return values;
    }

    public BigDecimal averageValue(ValueCatalog catalog) {
        return CaseRules.averageValue(weightArray(), rewardValues(catalog));
    }

    /** What this case returns on average for its price; displayed, never silently corrected. */
    public BigDecimal returnRate(ValueCatalog catalog) {
        return CaseRules.returnRate(weightArray(), rewardValues(catalog), priceValue(catalog));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CaseDefinition> CODEC = new StreamCodec<>() {
        @Override public CaseDefinition decode(RegistryFriendlyByteBuf buffer) {
            String id = buffer.readUtf(32);
            String name = buffer.readUtf(MAX_NAME_LENGTH);
            ResourceLocation priceItem = buffer.readResourceLocation();
            int priceCount = buffer.readVarInt();
            int count = buffer.readVarInt();
            if (count < CaseRules.MIN_REWARDS || count > CaseRules.MAX_REWARDS) {
                throw new IllegalArgumentException("Invalid case table length");
            }
            List<CaseReward> rewards = new ArrayList<>(count);
            for (int index = 0; index < count; index++) rewards.add(CaseReward.CODEC.decode(buffer));
            return new CaseDefinition(id, name, priceItem, priceCount, rewards);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, CaseDefinition data) {
            buffer.writeUtf(data.id(), 32);
            buffer.writeUtf(data.name(), MAX_NAME_LENGTH);
            buffer.writeResourceLocation(data.priceItem());
            buffer.writeVarInt(data.priceCount());
            buffer.writeVarInt(data.rewards().size());
            for (CaseReward reward : data.rewards()) CaseReward.CODEC.encode(buffer, reward);
        }
    };
}
