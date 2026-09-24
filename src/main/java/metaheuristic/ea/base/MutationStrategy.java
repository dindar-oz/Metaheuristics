package metaheuristic.ea.base;

import base.MutationOperator;
import base.OptimizationProblem;
import representation.base.Population;
import representation.base.Representation;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Decides how the mutation operators are applied to a population.
 * New solutions must be scored with the given evaluator so that the EA can count evaluations.
 */
public interface MutationStrategy {
    void applyMutations(OptimizationProblem problem, Population population, List<MutationOperator> mutationOperators,
                        ToDoubleFunction<Representation> evaluator);
}
