package core.algorithm.aco.problem.tsp;

import core.algorithm.aco.PheromoneMatrix;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.problems.tsp.TSP;
import core.representation.Permutation;

/**
 * Q-learning style pheromone for the TSP. Each edge of a tour moves towards the tour's
 * improvement over a reference upper bound:
 * {@code tau += learningRate * (max(0, UB - L) - tau)}, where {@code UB} is the
 * nearest-neighbour tour length. Tours worse than the bound pull their edges towards zero.
 */
public class TSPQPheromoneMatrix extends PheromoneMatrix {

    private static final double DEFAULT_LEARNING_RATE = 0.9;

    private final double learningRate;
    private double upperBound;
    private boolean symmetric;

    public TSPQPheromoneMatrix(double initialValue, int colonySize, double evaporationRatio) {
        this(initialValue, colonySize, evaporationRatio, DEFAULT_LEARNING_RATE);
    }

    public TSPQPheromoneMatrix(double initialValue, int colonySize, double evaporationRatio, double learningRate) {
        super(initialValue, colonySize, evaporationRatio);
        this.learningRate = learningRate;
    }

    @Override
    public void init(OptimizationProblem problem) {
        TSP tsp = (TSP) problem.model();
        upperBound = tsp.nearestNeighbourTourLength(0);
        symmetric = tsp.isSymmetric();
        allocate(tsp.getN(), tsp.getN(), initialValue);
    }

    /** @return the reference tour length the deposits are measured against */
    public double getUpperBound() {
        return upperBound;
    }

    @Override
    protected void deposit(OptimizationProblem problem, Solution s) {
        Permutation tour = (Permutation) s.getRepresentation();
        // Clamped at zero so that pheromone can never become negative.
        double target = Math.max(0, upperBound - s.objectiveValue());

        for (int i = 0; i < tour.size(); i++) {
            int c1 = tour.get(i);
            int c2 = tour.get((i + 1) % tour.size());
            blend(index(c1, c2), target, learningRate);
            if (symmetric)
                blend(index(c2, c1), target, learningRate);
        }
    }

    @Override
    protected PheromoneMatrix create(int colonySize) {
        return new TSPQPheromoneMatrix(initialValue, colonySize, evaporationRatio, learningRate);
    }
}
