package alg.ga;

import problem.OptimizationProblem;
import problem.ProblemModel;
import representation.Representation;
import representation.Solution;

import java.util.List;

public interface CrossOverOperator {
    <R extends Representation, PM extends ProblemModel<R>> List<Solution> apply(OptimizationProblem<PM, R> problem, Solution parent1, Solution parent2);
}
