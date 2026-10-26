package alg.ga;

import problem.OptimizationProblem;
import problem.ProblemModel;
import representation.Representation;
import representation.Solution;

import java.util.List;

public interface VictimSelector {
    <R extends Representation, PM extends ProblemModel<R>> List<Solution> select(OptimizationProblem<PM, R> problem, List<Solution> population, int count);
}
