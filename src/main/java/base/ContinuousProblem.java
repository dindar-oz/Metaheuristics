package base;

import representation.DoubleVector;
import util.random.RNG;

/**
 * A problem over a real-valued box-constrained search space.
 */
public interface ContinuousProblem extends OptimizationProblem<DoubleVector> {
    int getDimensionCount();

    double getUpperBound(int d);

    double getLowerBound(int d);

    // a solution drawn uniformly inside the bounds
    default DoubleVector randomSolution(RNG rng) {
        double[] values = new double[getDimensionCount()];
        for (int d = 0; d < values.length; d++)
            values[d] = rng.randDouble(getLowerBound(d), getUpperBound(d));
        return new DoubleVector(values);
    }
}
