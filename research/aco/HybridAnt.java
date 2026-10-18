package core.algorithm.aco;

import core.algorithm.AbstractSMetaheuristic;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.utils.random.RNG;

import java.util.function.Supplier;

/**
 * Ant that improves every constructed solution with a single-solution metaheuristic
 * (e.g. {@link core.algorithm.localsearch.LocalSearch}), the classic "ACO + local search"
 * hybrid.
 */
public class HybridAnt implements Ant {

    private final Ant internalAnt;
    private final Supplier<? extends AbstractSMetaheuristic> improverFactory;

    private OptimizationProblem problem;
    private AbstractSMetaheuristic improver;
    private Solution solution;

    /**
     * @param internalAnt     ant that builds the initial solution
     * @param improverFactory creates this ant's private improver; every ant needs its own
     *                        because improvers keep state and ants may run in parallel
     */
    public HybridAnt(Ant internalAnt, Supplier<? extends AbstractSMetaheuristic> improverFactory) {
        this.internalAnt = internalAnt;
        this.improverFactory = improverFactory;
    }

    @Override
    public void init(OptimizationProblem problem, PheromoneTrails trails, RNG rng) {
        internalAnt.init(problem, trails, rng);
        this.problem = problem;
        this.improver = improverFactory.get();
    }

    @Override
    public Solution constructSolution() {
        Solution constructed = internalAnt.constructSolution();

        improver.setCurrentSolution(problem, constructed);
        Solution improved = improver.perform(problem);

        // The improver may return null (no improving move found) or, in principle, something worse.
        solution = improved != null
                && problem.objectiveType().betterThan(improved.objectiveValue(), constructed.objectiveValue())
                ? improved : constructed;
        return solution;
    }

    @Override
    public Solution getSolution() {
        return solution;
    }
}
