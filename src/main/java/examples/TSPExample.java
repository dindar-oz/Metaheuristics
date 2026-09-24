package examples;

import base.InitialSolutionGenerator;
import base.MutationOperator;
import base.RandomPermutationSG;
import base.TerminalCondition;
import base.terminalcondition.NeighboringBasedTC;
import experiments.ConsoleERH;
import experiments.Experiment;
import experiments.ExperimentCase;
import metaheuristic.aco.ACO;
import metaheuristic.ea.EA;
import metaheuristic.ea.crossover.SimplePermutationCrossover;
import metaheuristic.ea.parentselector.TournamentParentSelector;
import metaheuristic.ls.LocalSearch;
import metaheuristic.sa.GeometricCooling;
import metaheuristic.sa.SA;
import operators.RandomRemoveReinsert;
import operators.TwoOpt;
import problems.tsp.TSPProblem;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Compares EA, SA, local search and ACO on a random 50-city Euclidean TSP instance.
 */
public class TSPExample {

    static final long MAX_EVALUATIONS = 50000;

    static List<ExperimentCase> createCases(TSPProblem problem) {
        InitialSolutionGenerator isg = new RandomPermutationSG(problem.getCityCount());
        List<MutationOperator> operators = Arrays.asList(new TwoOpt(), new RandomRemoveReinsert());

        EA ea = new EA(Collections.singletonList(new SimplePermutationCrossover()), operators,
                new TournamentParentSelector(3, 2), budget());

        SA sa = new SA(operators, new GeometricCooling(0.995), 1.0, 1e-6, budget());
        sa.setInnerLoopLength(20);

        LocalSearch ls = new LocalSearch(operators, 5000, budget());

        ACO aco = new ACO(20, budget());

        return Arrays.asList(
                new ExperimentCase("EA", ea, problem, isg),
                new ExperimentCase("SA", sa, problem, isg),
                new ExperimentCase("LocalSearch", ls, problem, isg),
                new ExperimentCase("ACO", aco, problem, isg));
    }

    static TerminalCondition budget() {
        return new NeighboringBasedTC(MAX_EVALUATIONS);
    }

    public static void main(String[] args) {
        TSPProblem problem = TSPProblem.randomEuclidean(50, 42);
        new Experiment(createCases(problem), 5, Collections.singletonList(new ConsoleERH())).run();
    }
}
