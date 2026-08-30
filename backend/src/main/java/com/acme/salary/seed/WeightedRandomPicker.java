package com.acme.salary.seed;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Picks a key from a weight map with probability proportional to its weight. */
final class WeightedRandomPicker {

    private WeightedRandomPicker() {}

    static <T> T pick(Map<T, Integer> weights, ThreadLocalRandom random) {
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (Map.Entry<T, Integer> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return entry.getKey();
            }
        }
        // Unreachable unless weights is empty; fall back to the last key rather than throw.
        List<T> keys = List.copyOf(weights.keySet());
        return keys.get(keys.size() - 1);
    }
}
