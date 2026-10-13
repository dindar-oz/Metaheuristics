package base;

import representation.base.Representation;
import util.random.RNG;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces starting solutions for a metaheuristic. Must keep no mutable state (it may be shared by concurrent runs).
 */
@FunctionalInterface
public interface InitialSolutionGenerator<S extends Representation<S>> {

    S generate(OptimizationProblem<S> problem, RNG rng);

    default List<S> generate(OptimizationProblem<S> problem, RNG rng, int count) {
        List<S> solutions = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            solutions.add(generate(problem, rng));
        return solutions;
    }

    // Always starts from (a copy of) the given solution
    static <S extends Representation<S>> InitialSolutionGenerator<S> fixed(S solution) {
        S copy = solution.copy();
        return (problem, rng) -> copy.copy();
    }
}
