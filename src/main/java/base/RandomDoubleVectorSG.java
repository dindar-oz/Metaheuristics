package base;

import representation.DoubleVector;
import representation.base.Representation;
import util.random.RNG;

/**
 * Generates DoubleVectors uniformly inside the bounds of a ContinuousProblem.
 */
public class RandomDoubleVectorSG implements InitialSolutionGenerator {

    @Override
    public Representation generate(OptimizationProblem problem, RNG rng) {
        ContinuousProblem cp = (ContinuousProblem) problem;
        double[] values = new double[cp.getDimensionCount()];
        for (int d = 0; d < values.length; d++) {
            double lb = cp.getLowerBound(d);
            values[d] = lb + rng.randDouble() * (cp.getUpperBound(d) - lb);
        }
        return new DoubleVector(values);
    }
}
