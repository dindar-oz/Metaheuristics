package testsupport;

import base.ContinuousProblem;
import problems.continuous.Sphere;
import base.MutationOperator;
import base.RandomPermutationSG;
import experiments.ExperimentCase;
import metaheuristic.aco.ACO;
import metaheuristic.de.DifferentialEvolution;
import metaheuristic.ea.EA;
import metaheuristic.ea.crossover.SimplePermutationCrossover;
import metaheuristic.ea.hook.AgeingHook;
import metaheuristic.ea.hook.ImmigrationHook;
import metaheuristic.ls.LocalSearch;
import metaheuristic.pso.PSO;
import metaheuristic.sa.GeometricCooling;
import metaheuristic.sa.SA;
import operators.RandomRemoveReinsert;
import operators.TwoOpt;
import problems.tsp.TSPProblem;
import representation.IntegerPermutation;

import java.util.Arrays;
import java.util.List;

/**
 * Small problems and one configured instance of every algorithm, shared by the tests.
 */
public final class Fixtures {

    public static final TSPProblem TSP = TSPProblem.randomEuclidean(20, 1);
    public static final ContinuousProblem SPHERE = new Sphere(5);

    private Fixtures() {
    }

    public static List<ExperimentCase<?, ?>> allAlgorithms() {
        RandomPermutationSG isg = new RandomPermutationSG(TSP.getCityCount());
        List<MutationOperator<IntegerPermutation>> ops = Arrays.asList(new TwoOpt(), new RandomRemoveReinsert<>());
        return Arrays.asList(
                ExperimentCase.of("EA", EA.builder(isg)
                        .crossovers(new SimplePermutationCrossover<>())
                        .mutations(ops)
                        .hook(new ImmigrationHook<>(20, 5))
                        .hook(new AgeingHook<>(50))
                        .populationSize(20)
                        .build(), TSP),
                ExperimentCase.of("SA", SA.builder(ops, isg).build(), TSP),
                ExperimentCase.of("LocalSearch", LocalSearch.builder(ops, isg).maxNonImproving(200).build(), TSP),
                ExperimentCase.of("ACO", ACO.builder().antCount(5).build(), TSP),
                ExperimentCase.of("PSO", PSO.builder().swarmSize(10).build(), SPHERE),
                ExperimentCase.of("DE", DifferentialEvolution.builder().populationSize(10).build(), SPHERE));
    }

    public static boolean isPermutation(IntegerPermutation p, int n) {
        if (p.getLength() != n)
            return false;
        boolean[] seen = new boolean[n];
        for (int i = 0; i < n; i++) {
            int v = p.get(i);
            if (v < 0 || v >= n || seen[v])
                return false;
            seen[v] = true;
        }
        return true;
    }
}
