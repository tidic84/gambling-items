package dev.gamblingitems.core.value;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

/** Recipe cost estimates, independent of Minecraft. Explicit prices always take precedence. */
public final class RecipeValues {
    private RecipeValues() {}

    /** Each ingredient slot lists its alternatives; repeated slots are paid for separately. */
    public record Recipe(String output, int count, List<List<String>> ingredients) {
        public Recipe {
            if (count < 1 || ingredients.isEmpty() || ingredients.stream().anyMatch(List::isEmpty)) {
                throw new IllegalArgumentException("A recipe needs an output and nonempty ingredients");
            }
            ingredients = ingredients.stream().map(List::copyOf).toList();
        }
    }

    public record Result(Map<String, Long> values, Set<String> fallbackItems, boolean converged) {}

    public static Result resolve(Map<String, Long> explicit, List<Recipe> recipes,
                                 ToLongFunction<String> fallback, long maximum) {
        if (maximum < 1 || explicit.values().stream().anyMatch(v -> v < 1 || v > maximum)) {
            throw new IllegalArgumentException("Invalid price limits");
        }
        var values = new HashMap<>(explicit);
        var outputs = new HashSet<String>();
        var all = new HashSet<String>();
        var dependents = new HashMap<String, List<Integer>>();
        for (int i = 0; i < recipes.size(); i++) {
            Recipe recipe = recipes.get(i);
            outputs.add(recipe.output());
            all.add(recipe.output());
            for (List<String> ingredient : recipe.ingredients()) {
                for (String item : ingredient) {
                    all.add(item);
                    dependents.computeIfAbsent(item, ignored -> new ArrayList<>()).add(i);
                }
            }
        }
        var fallbackItems = new HashSet<String>();
        for (String item : all) {
            if (!outputs.contains(item) && !values.containsKey(item)) {
                values.put(item, checkedFallback(fallback, item, maximum));
                fallbackItems.add(item);
            }
        }
        boolean converged = relax(values, explicit.keySet(), recipes, dependents, maximum);
        // Seed only cycles, not their downstream products: an unknown smithing template must
        // not turn an expensive netherite tool into another cheap fallback item.
        var pinned = new HashSet<>(explicit.keySet());
        seedCycles(values, recipes, fallback, maximum, fallbackItems, pinned);
        converged &= relax(values, pinned, recipes, dependents, maximum);
        for (String item : all) {
            if (!values.containsKey(item)) {
                values.put(item, checkedFallback(fallback, item, maximum));
                fallbackItems.add(item);
                converged = false;
            }
        }
        return new Result(Map.copyOf(values), Set.copyOf(fallbackItems), converged);
    }

    private record Walk(String item, java.util.Iterator<String> dependencies) {}

    private static void seedCycles(Map<String, Long> values, List<Recipe> recipes,
                                   ToLongFunction<String> fallback, long maximum,
                                   Set<String> fallbackItems, Set<String> pinned) {
        var graph = new HashMap<String, Set<String>>();
        for (Recipe recipe : recipes) {
            if (values.containsKey(recipe.output())) continue;
            var dependencies = graph.computeIfAbsent(recipe.output(), ignored -> new java.util.TreeSet<>());
            for (List<String> slot : recipe.ingredients()) {
                if (slot.stream().noneMatch(values::containsKey)) {
                    // Any one alternative is enough to establish a route for this slot.
                    dependencies.add(slot.stream().min(String::compareTo).orElseThrow());
                }
            }
        }
        var state = new HashMap<String, Integer>();
        var stack = new ArrayDeque<Walk>();
        for (String root : graph.keySet().stream().sorted().toList()) {
            if (state.containsKey(root)) continue;
            state.put(root, 1);
            stack.push(new Walk(root, graph.get(root).iterator()));
            while (!stack.isEmpty()) {
                Walk current = stack.peek();
                if (!current.dependencies().hasNext()) {
                    state.put(current.item(), 2);
                    stack.pop();
                    continue;
                }
                String next = current.dependencies().next();
                if (values.containsKey(next)) continue;
                if (state.getOrDefault(next, 0) == 1) {
                    values.put(next, checkedFallback(fallback, next, maximum));
                    fallbackItems.add(next);
                    pinned.add(next);
                } else if (!state.containsKey(next)) {
                    state.put(next, 1);
                    stack.push(new Walk(next, graph.getOrDefault(next, Set.of()).iterator()));
                }
            }
        }
    }

    private static long checkedFallback(ToLongFunction<String> fallback, String item, long maximum) {
        long value = fallback.applyAsLong(item);
        if (value < 1 || value > maximum) throw new IllegalArgumentException("Invalid fallback for " + item);
        return value;
    }

    private static boolean relax(Map<String, Long> values, Set<String> pinned, List<Recipe> recipes,
                                 Map<String, List<Integer>> dependents, long maximum) {
        var queue = new ArrayDeque<Integer>();
        boolean[] queued = new boolean[recipes.size()];
        for (int i = 0; i < recipes.size(); i++) { queue.add(i); queued[i] = true; }
        // Protect server startup from pathological conversion loops supplied by a datapack.
        long budget = Math.max(1024L, recipes.size() * 256L);
        while (!queue.isEmpty() && budget-- > 0) {
            int index = queue.removeFirst();
            queued[index] = false;
            Recipe recipe = recipes.get(index);
            if (pinned.contains(recipe.output())) continue;
            long cost = 0;
            boolean known = true;
            for (List<String> ingredient : recipe.ingredients()) {
                long cheapest = Long.MAX_VALUE;
                boolean found = false;
                for (String item : ingredient) {
                    if (values.containsKey(item)) { cheapest = Math.min(cheapest, values.get(item)); found = true; }
                }
                if (!found) { known = false; break; }
                cost = Math.min(Long.MAX_VALUE - cheapest, cost) + cheapest;
            }
            if (!known) continue;
            long value = Math.max(1, Math.min(maximum, cost / recipe.count()));
            if (values.containsKey(recipe.output()) && value >= values.get(recipe.output())) continue;
            values.put(recipe.output(), value);
            for (int dependent : dependents.getOrDefault(recipe.output(), List.of())) {
                if (!queued[dependent]) { queue.addLast(dependent); queued[dependent] = true; }
            }
        }
        return queue.isEmpty();
    }
}
