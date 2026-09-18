package dev.gamblingitems.fabric.tradeup;

import dev.gamblingitems.core.tradeup.TradeUpRules;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * The rewards offered for one stake, with their weights.
 * Built the same way on both sides from the same inputs, so the screen can show the real odds
 * before anything is consumed. The server still rebuilds it when the wager is accepted.
 */
public record TradeUpTable(List<ValueCatalog.Entry> rewards, List<Integer> weights,
                           long totalStake, long bestUnitValue) {
    public TradeUpTable {
        rewards = List.copyOf(rewards);
        weights = List.copyOf(weights);
        if (rewards.size() != weights.size() || rewards.isEmpty()) {
            throw new IllegalArgumentException("Invalid trade up table");
        }
    }

    public int[] weightArray() {
        int[] array = new int[weights.size()];
        for (int index = 0; index < array.length; index++) array[index] = weights.get(index);
        return array;
    }

    /** Weights are parts per million, so a percentage is the weight scaled by four decimals. */
    public BigDecimal percentOf(int index) {
        return BigDecimal.valueOf(weights.get(index), 4);
    }

    public BigDecimal averageValue(TradeUpSetup setup) {
        return setup.settings().rules().averageValue(weightArray(),
                rewards.stream().map(ValueCatalog.Entry::value).toList());
    }

    /** One value per item held in the input slots, so the screen can explain what is missing. */
    public static List<Long> units(TradeUpSetup setup, Container inputs, int inputSlots) {
        List<Long> units = new ArrayList<>();
        for (int slot = 0; slot < inputSlots; slot++) {
            ItemStack stack = inputs.getItem(slot);
            if (stack.isEmpty()) continue;
            long unit = setup.catalog().unitValue(stack);
            for (int count = 0; count < stack.getCount(); count++) units.add(unit);
        }
        return units;
    }

    /** Empty when the stake is not admissible, or when no reward set can average the return. */
    public static Optional<TradeUpTable> build(TradeUpSetup setup, Container inputs, int inputSlots) {
        TradeUpRules rules = setup.settings().rules();
        List<Long> units = units(setup, inputs, inputSlots);
        if (!rules.acceptsStake(units)) return Optional.empty();
        long total = 0;
        long best = 0;
        for (long unit : units) {
            total += unit;
            best = Math.max(best, unit);
        }
        List<ValueCatalog.Entry> eligible = new ArrayList<>();
        for (ValueCatalog.Entry entry : setup.catalog().entries()) {
            if (rules.isEligibleReward(entry.value(), best, total)) eligible.add(entry);
        }
        eligible.sort(Comparator.comparingLong(ValueCatalog.Entry::value)
                .thenComparing(entry -> entry.id().toString()));
        List<ValueCatalog.Entry> offered = spread(eligible, setup.settings().rewardCount());
        List<Long> values = offered.stream().map(ValueCatalog.Entry::value).toList();
        if (!rules.canBuildTable(total, values)) return Optional.empty();
        int[] weights = rules.weights(total, values);
        List<Integer> boxed = new ArrayList<>(weights.length);
        for (int weight : weights) boxed.add(weight);
        return Optional.of(new TradeUpTable(offered, boxed, total, best));
    }

    /** Keeps the cheapest and the dearest reward, then spreads the rest evenly across the catalogue. */
    private static List<ValueCatalog.Entry> spread(List<ValueCatalog.Entry> eligible, int wanted) {
        if (eligible.size() <= wanted) return eligible;
        List<ValueCatalog.Entry> offered = new ArrayList<>(wanted);
        for (int index = 0; index < wanted; index++) {
            int position = (int) Math.round((double) index * (eligible.size() - 1) / (wanted - 1));
            ValueCatalog.Entry entry = eligible.get(position);
            if (!offered.contains(entry)) offered.add(entry);
        }
        return offered;
    }
}
