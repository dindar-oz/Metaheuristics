package base;

import representation.base.Representation;
import util.random.RNG;
import util.random.RandUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces starting solutions for a metaheuristic.
 */
public interface InitialSolutionGenerator {

    Representation generate(OptimizationProblem problem, RNG rng);

    default Representation generate(OptimizationProblem problem) {
        return generate(problem, RandUtil.getDefaultRNG());
    }

    default List<Representation> generate(OptimizationProblem problem, int count) {
        List<Representation> solutions = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            solutions.add(generate(problem));
        return solutions;
    }
}
