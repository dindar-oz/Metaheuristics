package experiments;

import metaheuristic.run.RunResult;

import java.util.List;

/**
 * Prints one summary line per case: best/mean/std of the final best cost, mean evaluations and
 * mean time (ms) at which the best solution was found.
 */
public class ConsoleERH implements ExperimentResultHandler {

    @Override
    public void begin(long baseSeed) {
        System.out.printf("seed %d%n%-20s %14s %14s %14s %14s %12s%n", baseSeed,
                "case", "best", "mean", "std", "meanEvals", "meanBestMs");
    }

    @Override
    public void handle(ExperimentCase<?, ?> experimentCase, List<RunResult<?>> runs) {
        double best = Double.POSITIVE_INFINITY, sum = 0, evals = 0, bestTime = 0;
        for (RunResult<?> r : runs) {
            best = Math.min(best, r.bestCost());
            sum += r.bestCost();
            evals += r.evaluations();
            bestTime += r.timeToBestMillis();
        }
        int n = runs.size();
        double mean = sum / n;
        double var = 0;
        for (RunResult<?> r : runs)
            var += (r.bestCost() - mean) * (r.bestCost() - mean);

        System.out.printf("%-20s %14.6f %14.6f %14.6f %14.1f %12.1f%n", experimentCase.getTitle(),
                best, mean, Math.sqrt(var / n), evals / n, bestTime / n);
    }
}
