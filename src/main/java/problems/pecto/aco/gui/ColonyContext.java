package problems.pecto.aco.gui;

import metaheuristic.run.Metrics;
import metaheuristic.run.Progress;
import metaheuristic.run.SearchContext;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The search context of a diagnosed run: what metaheuristic.run.SearchRun does for a normal run (counting
 * evaluations, keeping the best solution, adding the diagnostic metrics), with a state that can be saved in a
 * checkpoint and restored. Confined to the run's thread.
 */
final class ColonyContext implements SearchContext<PECTOSolution>, Progress {

    private static final int DIVERSITY_SAMPLE = 16;     // as in SearchRun

    private final PECTOProblem problem;
    private final long maxEvaluations;                  // 0: no limit
    private final long startNanos = System.nanoTime();
    private CheckpointRNG rng;
    private long evaluations, iterations, bestEvaluation, bestIteration;
    private double bestCost = Double.POSITIVE_INFINITY;
    private PECTOSolution best;
    private Map<String, Double> metrics = Collections.emptyMap();

    ColonyContext(PECTOProblem problem, long seed, long maxEvaluations) {
        this.problem = problem;
        this.rng = new CheckpointRNG(seed);
        this.maxEvaluations = maxEvaluations;
    }

    @Override
    public CheckpointRNG rng() {
        return rng;
    }

    // ColonyRun checks shouldStop() before every evaluation, as PECTOACO does
    @Override
    public double evaluate(PECTOSolution solution) {
        evaluations++;
        double cost = problem.cost(solution);
        if (cost < bestCost) {
            bestCost = cost;
            best = solution;
            bestEvaluation = evaluations;
            bestIteration = iterations;
        }
        return cost;
    }

    @Override
    public boolean shouldStop() {
        return maxEvaluations > 0 && evaluations >= maxEvaluations;
    }

    @Override
    public void iterationCompleted(Map<String, Double> metrics, List<? extends PECTOSolution> population) {
        Map<String, Double> all = new LinkedHashMap<>(metrics);
        all.put(Metrics.STAGNATION, (double) (iterations - bestIteration));
        int n = population.size();
        if (n >= 2) {
            Set<PECTOSolution> distinct = new HashSet<>(population);
            all.put(Metrics.DUPLICATES, (double) (n - distinct.size()) / n);
            int k = Math.min(n, DIVERSITY_SAMPLE);
            double sum = 0;
            for (int i = 0; i < k; i++)
                for (int j = i + 1; j < k; j++)
                    sum += population.get(i * n / k).distanceTo(population.get(j * n / k));
            all.put(Metrics.DIVERSITY, sum / (k * (k - 1) / 2.0));
        }
        iterations++;
        this.metrics = Collections.unmodifiableMap(all);
    }

    @Override
    public Progress progress() {
        return this;
    }

    @Override
    public PECTOSolution bestSolution() {
        return best;
    }

    @Override
    public long iterations() {
        return iterations;
    }

    @Override
    public long evaluations() {
        return evaluations;
    }

    @Override
    public long elapsedMillis() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    @Override
    public double bestCost() {
        return bestCost;
    }

    @Override
    public long timeToBestMillis() {
        return 0;   // not tracked: a diagnosed run pauses and replays
    }

    @Override
    public long evaluationsSinceImprovement() {
        return evaluations - bestEvaluation;
    }

    @Override
    public long iterationsSinceImprovement() {
        return iterations - bestIteration;
    }

    @Override
    public Map<String, Double> metrics() {
        return metrics;
    }

    // ---- checkpoints

    record Snapshot(CheckpointRNG rng, long evaluations, long iterations, long bestEvaluation, long bestIteration,
                    double bestCost, PECTOSolution best, Map<String, Double> metrics) {
    }

    Snapshot snapshot() {
        return new Snapshot(rng.copy(), evaluations, iterations, bestEvaluation, bestIteration, bestCost, best, metrics);
    }

    void restore(Snapshot s) {
        rng = s.rng().copy();
        evaluations = s.evaluations();
        iterations = s.iterations();
        bestEvaluation = s.bestEvaluation();
        bestIteration = s.bestIteration();
        bestCost = s.bestCost();
        best = s.best();
        metrics = s.metrics();
    }
}
