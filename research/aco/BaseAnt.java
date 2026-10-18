package core.algorithm.aco;

import core.base.OptimizationProblem;
import core.base.Solution;
import core.utils.random.RNG;

/**
 * Template for step-wise constructive ants:
 * {@link #reset()} → repeat {@link #next()} until {@link #solutionConstructed()} → {@link #buildSolution()}.
 */
public abstract class BaseAnt implements Ant {

    protected OptimizationProblem problem;
    protected PheromoneTrails pheromoneTrails;
    protected RNG rng;
    protected Solution solution;

    @Override
    public void init(OptimizationProblem problem, PheromoneTrails pheromoneTrails, RNG rng) {
        this.problem = problem;
        this.pheromoneTrails = pheromoneTrails;
        this.rng = rng;
    }

    @Override
    public final Solution constructSolution() {
        reset();
        while (!solutionConstructed()) {
            next();
        }
        solution = buildSolution();
        return solution;
    }

    @Override
    public Solution getSolution() {
        return solution;
    }

    /** Clears the partial solution so that a new construction can start. */
    protected abstract void reset();

    /** Performs one construction step (e.g. visits one more city). */
    protected abstract void next();

    protected abstract boolean solutionConstructed();

    /** Turns the finished partial solution into an evaluated {@link Solution}. */
    protected abstract Solution buildSolution();

    /**
     * Checks that the trails are a {@link PheromoneMatrix}, the type the concrete ants in this
     * project read from, and throws a readable error instead of a later ClassCastException.
     */
    protected static PheromoneMatrix requireMatrix(PheromoneTrails trails, Class<? extends Ant> antType) {
        if (trails instanceof PheromoneMatrix matrix)
            return matrix;
        throw new IllegalArgumentException(antType.getSimpleName() + " requires a PheromoneMatrix but got "
                + (trails == null ? "null" : trails.getClass().getSimpleName()));
    }
}
