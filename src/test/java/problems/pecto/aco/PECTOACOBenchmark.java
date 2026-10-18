package problems.pecto.aco;

import metaheuristic.run.RunResult;
import metaheuristic.run.Runner;
import metaheuristic.termination.EvaluationBasedTC;
import problems.pecto.PECTOInstances;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;

/**
 * Wall time of PECTOACO with its defaults on a Table I instance, for comparing two builds of the same search (same
 * seed, same result): {@code java -cp <classes> problems.pecto.aco.PECTOACOBenchmark [instance] [iterations] [runs]}.
 * Prints one line per run (the first ones warm the JIT up), then the median of the remaining runs.
 */
public final class PECTOACOBenchmark {

    public static void main(String[] args) {
        int instance = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        int runs = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        int warmup = Math.min(2, runs - 1);
        PECTOProblem problem = PECTOInstances.paper(instance, 42);
        PECTOACO aco = PECTOACO.builder().build();
        // the greedy solution, then per iteration: the ants, the local search neighbors and the optimal hospitals
        int evaluations = 1 + iterations * (20 + 100 + 1);
        long[] millis = new long[runs - warmup];
        for (int r = 0; r < runs; r++) {
            long start = System.nanoTime();
            RunResult<PECTOSolution> result = Runner.of(aco).seed(1000 + r % 2)
                    .stopWhen(new EvaluationBasedTC(evaluations)).run(problem);
            long ms = (System.nanoTime() - start) / 1_000_000;
            System.out.printf("run %d: %d ms, cost %.4f, %d iterations%s%n", r, ms, result.bestCost(), result.iterations(),
                    r < warmup ? " (warm-up)" : "");
            if (r >= warmup)
                millis[r - warmup] = ms;
        }
        java.util.Arrays.sort(millis);
        System.out.println("median " + millis[millis.length / 2] + " ms");
    }
}
