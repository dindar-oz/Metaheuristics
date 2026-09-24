package experiments;

import metaheuristic.MetaHeuristic;

import java.util.List;

/**
 * Runs every case repeatCount times and reports, per case, the best/mean/std of the final best cost,
 * the mean evaluation count and the mean time (ms) at which the best solution was found.
 */
public class Experiment {

    public static final String HEADER = String.format("%-20s %14s %14s %14s %14s %12s%n",
            "case", "best", "mean", "std", "meanEvals", "meanBestMs");

    private final List<ExperimentCase> cases;
    private final int repeatCount;
    private final List<ExperimentResultHandler> resultHandlers;

    public Experiment(List<ExperimentCase> cases, int repeatCount, List<ExperimentResultHandler> resultHandlers) {
        this.cases = cases;
        this.repeatCount = repeatCount;
        this.resultHandlers = resultHandlers;
    }

    public void run() {
        resultHandlers.forEach(h -> h.handle(HEADER));
        for (ExperimentCase experimentCase : cases) {
            String result = runCase(experimentCase);
            resultHandlers.forEach(h -> h.handle(result));
        }
    }

    private String runCase(ExperimentCase experimentCase) {
        MetaHeuristic alg = experimentCase.getAlg();
        double[] costs = new double[repeatCount];
        double evals = 0;
        double bestTime = 0;

        for (int repeat = 0; repeat < repeatCount; repeat++) {
            alg.perform(experimentCase.getProblem(), experimentCase.getIsg());
            costs[repeat] = alg.getBestKnownCost();
            evals += alg.getNeighboringCount();
            bestTime += alg.getBestAchieveTime();
        }

        double best = Double.MAX_VALUE, sum = 0;
        for (double c : costs) {
            best = Math.min(best, c);
            sum += c;
        }
        double mean = sum / repeatCount;
        double var = 0;
        for (double c : costs)
            var += (c - mean) * (c - mean);
        double std = Math.sqrt(var / repeatCount);

        return String.format("%-20s %14.6f %14.6f %14.6f %14.1f %12.1f%n", experimentCase.getTitle(),
                best, mean, std, evals / repeatCount, bestTime / repeatCount);
    }
}
