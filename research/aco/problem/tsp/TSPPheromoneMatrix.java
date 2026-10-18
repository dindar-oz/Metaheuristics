package core.algorithm.aco.problem.tsp;

import core.algorithm.aco.PheromoneMatrix;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.problems.tsp.TSP;
import core.representation.Permutation;

/**
 * Ant System pheromone for the TSP: an {@code n x n} edge matrix where each tour deposits
 * {@code 1 / L} on every edge it uses ({@code L} = tour length).
 */
public class TSPPheromoneMatrix extends PheromoneMatrix {

    /** Pass as {@code initialValue} to use the Ant System default {@code tau0 = m / C_nn}. */
    public static final double AUTO_INITIAL_VALUE = 0;

    private boolean symmetric;
    private int n;

    /**
     * @param initialValue initial pheromone; any value {@code <= 0} means
     *                     {@code colonySize / (nearest-neighbour tour length)}, which puts it on
     *                     the same scale as the {@code 1/L} deposits
     */
    public TSPPheromoneMatrix(double initialValue, int colonySize, double evaporationRatio) {
        super(initialValue, colonySize, evaporationRatio);
    }

    /** Matrix with the recommended {@code tau0 = m / C_nn} initialisation. */
    public static TSPPheromoneMatrix withNearestNeighbourInit(int colonySize, double evaporationRatio) {
        return new TSPPheromoneMatrix(AUTO_INITIAL_VALUE, colonySize, evaporationRatio);
    }

    @Override
    public void init(OptimizationProblem problem) {
        TSP tsp = (TSP) problem.model();
        n = tsp.getN();
        symmetric = tsp.isSymmetric();
        double tau0 = initialValue > 0 ? initialValue : colonySize / tsp.nearestNeighbourTourLength(0);
        allocate(n, n, tau0);
    }

    @Override
    protected void deposit(OptimizationProblem problem, Solution s) {
        Permutation tour = (Permutation) s.getRepresentation();
        double delta = 1.0 / s.objectiveValue();
        for (int i = 0; i < tour.size(); i++) {
            int c1 = tour.get(i);
            int c2 = tour.get((i + 1) % tour.size());
            add(index(c1, c2), delta);
            if (symmetric)
                add(index(c2, c1), delta); // the edge can be travelled in both directions
        }
    }

    @Override
    protected PheromoneMatrix create(int colonySize) {
        return new TSPPheromoneMatrix(initialValue, colonySize, evaporationRatio);
    }
}
