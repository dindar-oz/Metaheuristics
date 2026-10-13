package examples;

import base.MutationOperator;
import base.RandomPermutationSG;
import metaheuristic.aco.ACO;
import metaheuristic.listener.Listeners;
import metaheuristic.run.ProgressSnapshot;
import metaheuristic.run.RunHandle;
import metaheuristic.run.RunResult;
import metaheuristic.run.Runner;
import metaheuristic.sa.GeometricCooling;
import metaheuristic.sa.SA;
import metaheuristic.termination.EvaluationBasedTC;
import operators.RandomRemoveReinsert;
import operators.TwoOpt;
import problems.tsp.TSPProblem;
import representation.IntegerPermutation;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shows how to observe and control runs:
 * 1) a synchronous run with a throttled console listener and a per-run CSV trace written on a background thread,
 * 2) a background run without a stop condition, polled through its RunHandle and then cancelled.
 */
public class MonitoringExample {

    public static void main(String[] args) throws Exception {
        TSPProblem problem = TSPProblem.randomEuclidean(100, 7);

        // 1) synchronous run
        List<MutationOperator<IntegerPermutation>> operators = Arrays.asList(new TwoOpt(), new RandomRemoveReinsert<>());
        SA<IntegerPermutation> sa = SA.builder(operators, new RandomPermutationSG(problem.getCityCount()))
                .cooling(new GeometricCooling(0.999))
                .innerLoopLength(20)
                .build();

        RunResult<IntegerPermutation> result = Runner.of(sa)
                .seed(1)
                .stopWhen(new EvaluationBasedTC(200_000))
                .listener(Listeners.throttled(Listeners.console(), 250))
                .listenerPerRun(info -> Listeners.async(Listeners.csv(Paths.get("results", "sa-trace-" + info.seed() + ".csv"))))
                .run(problem);
        System.out.println("SA finished: " + result);
        System.out.println("best tour starts with city " + result.bestSolution().get(0)); // typed result, no cast

        // 2) background run, polled and cancelled from this thread
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RunHandle<IntegerPermutation> handle = Runner.of(ACO.builder().build()).seed(2).start(problem, executor); // no stop condition
        for (int i = 0; i < 4; i++) {
            Thread.sleep(500);
            ProgressSnapshot p = handle.latestProgress();
            System.out.println("ACO progress: " + p);
        }
        handle.cancel();
        System.out.println("ACO finished: " + handle.result().get());
        executor.shutdown();
    }
}
