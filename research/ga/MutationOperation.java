package alg.ga;

import problem.OptimizationProblem;
import problem.ProblemModel;
import representation.Representation;
import representation.Solution;

public interface MutationOperation {
    <R extends Representation, PM extends ProblemModel<R>> void apply(OptimizationProblem<PM, R> problem, Solution s);
}
