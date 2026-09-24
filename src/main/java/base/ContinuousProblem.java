package base;

/**
 * A problem over a real-valued box-constrained search space (solutions are DoubleVectors).
 */
public interface ContinuousProblem extends OptimizationProblem {
    int getDimensionCount();
    double getUpperBound(int d);
    double getLowerBound(int d);
}
