package experiments;

import base.InitialSolutionGenerator;
import base.OptimizationProblem;
import metaheuristic.MetaHeuristic;

/**
 * One algorithm-problem pair of an experiment.
 */
public class ExperimentCase {
    private final String title;
    private final MetaHeuristic alg;
    private final OptimizationProblem problem;
    private final InitialSolutionGenerator isg;

    public ExperimentCase(String title, MetaHeuristic alg, OptimizationProblem problem, InitialSolutionGenerator isg) {
        this.title = title;
        this.alg = alg;
        this.problem = problem;
        this.isg = isg;
    }

    public String getTitle() {
        return title;
    }

    public MetaHeuristic getAlg() {
        return alg;
    }

    public OptimizationProblem getProblem() {
        return problem;
    }

    public InitialSolutionGenerator getIsg() {
        return isg;
    }

    @Override
    public String toString() {
        return title;
    }
}
