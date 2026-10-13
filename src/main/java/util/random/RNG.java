package util.random;

import java.util.ArrayList;
import java.util.List;

/**
 * Source of randomness. Every run owns its own RNG instance (see metaheuristic.run.SearchContext#rng()),
 * so implementations do not need to be thread-safe and must never be shared between threads.
 */
public interface RNG {

    // uniform integer in [0, max)
    int randInt(int max);

    // uniform double in [0, 1)
    double randDouble();

    default double randDouble(double lb, double ub) {
        return lb + randDouble() * (ub - lb);
    }

    default boolean rollDice(double probability) {
        return randDouble() < probability;
    }

    // Selects an index with probability proportional to its weight
    default int rouletteSelect(double[] weights) {
        double sum = 0;
        for (double w : weights)
            sum += w;
        double value = randDouble() * sum;
        for (int i = 0; i < weights.length; i++) {
            value -= weights[i];
            if (value <= 0)
                return i;
        }
        return weights.length - 1; // rounding errors
    }

    // Selects an index with probability decreasing with its weight (e.g. lower cost is more likely)
    default int rouletteSelectInverse(double[] weights) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (double w : weights) {
            min = Math.min(min, w);
            max = Math.max(max, w);
        }
        double[] inverted = new double[weights.length];
        for (int i = 0; i < weights.length; i++)
            inverted[i] = min + max - weights[i];
        return rouletteSelect(inverted);
    }

    // count distinct integers from [0, max), in random order
    default List<Integer> distinctInts(int max, int count) {
        if (count > max)
            throw new IllegalArgumentException("Cannot select " + count + " distinct values from " + max);
        int[] values = new int[max];
        for (int i = 0; i < max; i++)
            values[i] = i;
        List<Integer> selected = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int j = i + randInt(max - i);
            int tmp = values[i];
            values[i] = values[j];
            values[j] = tmp;
            selected.add(values[i]);
        }
        return selected;
    }
}
