package metaheuristic.aco;

import base.OptimizationProblem;
import representation.IntegerPermutation;

/**
 * A problem whose solutions are tours (IntegerPermutations) over a weighted graph, e.g. TSP.
 */
public interface GraphProblem extends OptimizationProblem<IntegerPermutation> {
    int getNodeCount();
    double getDistance(int from, int to);
}
