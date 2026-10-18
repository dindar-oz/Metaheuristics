package core.algorithm.aco;

import core.base.OptimizationProblem;
import core.base.Solution;
import core.utils.random.RNG;

/**
 * A single ant: builds one solution at a time, guided by the pheromone trails.
 * <p>
 * An ant instance keeps construction state, so it must be used by one thread at a time.
 * {@link ACO} guarantees this in every execution mode.
 */
public interface Ant {

    /**
     * Binds the ant to a problem, the trails it reads and its private random generator.
     * Called once at the start of every run.
     */
    void init(OptimizationProblem problem, PheromoneTrails pheromoneTrails, RNG rng);

    /** Builds a new solution from scratch and returns it. */
    Solution constructSolution();

    /** @return the solution produced by the last {@link #constructSolution()} call */
    Solution getSolution();
}
