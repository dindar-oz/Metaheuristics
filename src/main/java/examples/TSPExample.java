package examples;

import base.InitialSolutionGenerator;
import base.MutationOperator;
import base.RandomPermutationSG;
import experiments.ConsoleERH;
import experiments.CsvERH;
import experiments.Experiment;
import experiments.ExperimentCase;
import metaheuristic.aco.ACO;
import metaheuristic.ea.EA;
import metaheuristic.ea.crossover.SimplePermutationCrossover;
import metaheuristic.ea.parentselector.TournamentParentSelector;
import metaheuristic.ls.LocalSearch;
import metaheuristic.sa.GeometricCooling;
import metaheuristic.sa.SA;
import metaheuristic.termination.EvaluationBasedTC;
import metaheuristic.termination.TerminalCondition;
import operators.RandomRemoveReinsert;
import operators.TwoOpt;
import problems.tsp.TSPProblem;
import representation.IntegerPermutation;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Compares EA, SA, local search and ACO on a random 50-city Euclidean TSP instance.
 */
public class TSPExample {

    static final TerminalCondition BUDGET = new EvaluationBasedTC(50000);

    static List<ExperimentCase<IntegerPermutation, ?>> createCases(TSPProblem problem) {
        InitialSolutionGenerator<IntegerPermutation> isg = new RandomPermutationSG(problem.getCityCount());
        List<MutationOperator<IntegerPermutation>> operators = Arrays.asList(new TwoOpt(), new RandomRemoveReinsert<>());

        EA<IntegerPermutation> ea = EA.builder(isg)
                .crossovers(new SimplePermutationCrossover<>())
                .mutations(operators)
                .parentSelector(new TournamentParentSelector(3, 2))
                .build();

        SA<IntegerPermutation> sa = SA.builder(operators, isg)
                .cooling(new GeometricCooling(0.995))
                .initialTemperature(1.0)
                .minimumTemperature(1e-6)
                .innerLoopLength(20)
                .build();

        LocalSearch<IntegerPermutation> ls = LocalSearch.builder(operators, isg).maxNonImproving(5000).build();

        ACO aco = ACO.builder().antCount(20).build();

        return Arrays.asList(
                ExperimentCase.of("EA", ea, problem),
                ExperimentCase.of("SA", sa, problem),
                ExperimentCase.of("LocalSearch", ls, problem),
                ExperimentCase.of("ACO", aco, problem));
    }

    public static void main(String[] args) {
        TSPProblem problem = TSPProblem.randomEuclidean(50, 42);
        Experiment.of(createCases(problem))
                .repeats(5)
                .seed(42)
                .stopWhen(BUDGET)
                .threads(Runtime.getRuntime().availableProcessors())
                .handler(new ConsoleERH())
                .handler(new CsvERH(Paths.get("results", "tsp.csv")))
                .run();
    }
}
