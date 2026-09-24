package metaheuristic.aco;

import base.OptimizationProblem;

/**
 * A problem whose solutions are tours (IntegerPermutations) over a weighted graph, e.g. TSP.
 */
public interface GraphProblem extends OptimizationProblem {
    int getNodeCount();
    double getDistance(int from, int to);
}
