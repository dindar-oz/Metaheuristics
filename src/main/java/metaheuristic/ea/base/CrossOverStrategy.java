package metaheuristic.ea.base;

import base.OptimizationProblem;
import representation.base.Individual;
import representation.base.Representation;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Decides how the crossover operators produce offsprings from the selected parents.
 * Offsprings must be scored with the given evaluator so that the EA can count evaluations.
 */
public interface CrossOverStrategy {
    List<Individual> generateOffsprings(OptimizationProblem problem, List<Individual> parents, List<CrossOverOperator> crossOverOperators,
                                        ToDoubleFunction<Representation> evaluator);
}
