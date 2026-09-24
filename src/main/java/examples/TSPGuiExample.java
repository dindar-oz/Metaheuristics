package examples;

import base.InitialSolutionGenerator;
import experiments.ExperimentCase;
import gui.MHRunnerPanel;
import metaheuristic.AbstractMetaheuristic;
import problems.tsp.TSPProblem;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs the TSPExample algorithms in parallel and plots their convergence.
 */
public class TSPGuiExample {

    public static void main(String[] args) {
        TSPProblem problem = TSPProblem.randomEuclidean(50, 42);
        List<ExperimentCase> cases = TSPExample.createCases(problem);

        List<AbstractMetaheuristic> algs = cases.stream()
                .map(c -> (AbstractMetaheuristic) c.getAlg()).collect(Collectors.toList());
        List<InitialSolutionGenerator> generators = cases.stream()
                .map(ExperimentCase::getIsg).collect(Collectors.toList());

        MHRunnerPanel.showComparison("TSP - Metaheuristic Comparison", algs, generators, problem);
    }
}
