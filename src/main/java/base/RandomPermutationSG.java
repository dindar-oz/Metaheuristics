package base;

import representation.IntegerPermutation;
import representation.base.Representation;
import util.random.RNG;

/**
 * Generates uniformly random permutations of 0..size-1.
 */
public class RandomPermutationSG implements InitialSolutionGenerator {
    private final int size;

    public RandomPermutationSG(int size) {
        this.size = size;
    }

    @Override
    public Representation generate(OptimizationProblem problem, RNG rng) {
        int[] values = new int[size];
        for (int i = 0; i < size; i++)
            values[i] = i;

        // Fisher-Yates shuffle
        for (int i = size - 1; i > 0; i--) {
            int j = rng.randInt(i + 1);
            int tmp = values[i];
            values[i] = values[j];
            values[j] = tmp;
        }
        return new IntegerPermutation(values);
    }
}
