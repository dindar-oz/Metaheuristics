package core.algorithm.aco;

import core.base.OptimizationProblem;
import core.base.Solution;

import java.util.List;

/**
 * The colony's shared memory: pheromone values that ants read while building solutions and
 * that are reinforced by good solutions and decay over time.
 */
public interface PheromoneTrails {

    /** (Re)allocates and resets the trails for {@code problem}; called at the start of every run. */
    void init(OptimizationProblem problem);

    /**
     * Applies one pheromone update for a single solution: evaporation by
     * {@code evaporationRatio / colonySize} followed by a deposit proportional to the
     * solution's quality.
     * <p>
     * Evaporating {@code rho/m} once per ant means a whole colony iteration of {@code m} ants
     * decays trails by {@code (1 - rho/m)^m ≈ e^-rho}, and this stays the same whether the
     * updates happen once per iteration (sequential/synchronous) or one ant at a time
     * (asynchronous).
     * <p>
     * Implementations must be safe to call concurrently with reads and with other updates.
     */
    void update(OptimizationProblem problem, Solution solution);

    /** Applies {@link #update(OptimizationProblem, Solution)} for every solution, in order. */
    default void update(OptimizationProblem problem, List<? extends Solution> solutions) {
        for (Solution s : solutions) {
            update(problem, s);
        }
    }

    double getEvaporationRatio();

    /**
     * Creates a fresh, uninitialised trail with the same parameters but sized for a colony of
     * {@code colonySize} ants. The island (multi-colony) model uses it to give every island its
     * own trails.
     */
    default PheromoneTrails forColony(int colonySize) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support the island model");
    }
}
