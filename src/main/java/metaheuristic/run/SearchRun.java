package metaheuristic.run;

import base.OptimizationProblem;
import metaheuristic.termination.TerminalCondition;
import representation.base.Representation;
import util.random.RNG;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The live state of one run. Confined to the run's thread; other threads only see ProgressSnapshots
 * published through the RunHandle.
 */
final class SearchRun<S extends Representation<S>> implements SearchContext<S>, Progress {

    private final OptimizationProblem<S> problem;
    private final RNG rng;
    private final TerminalCondition stopCondition;
    private final List<SearchListener> listeners;
    private final RunHandle<S> handle;
    private final boolean diagnostics;
    private boolean distanceDefined = true; // until distanceTo() throws UnsupportedOperationException
    private final long startNanos = System.nanoTime();

    private long iterations = 0;
    private long evaluations = 0;
    private double bestCost = Double.POSITIVE_INFINITY;
    private S bestSolution = null; // private copy, replaced (never modified) on improvement
    private long bestEvaluation = 0;
    private long bestIteration = 0;
    private long timeToBestMillis = 0;
    private Map<String, Double> metrics = Collections.emptyMap();
    private StopReason stopReason = null;

    SearchRun(OptimizationProblem<S> problem, RNG rng, TerminalCondition stopCondition,
              List<SearchListener> listeners, RunHandle<S> handle, boolean diagnostics) {
        this.problem = problem;
        this.rng = rng;
        this.stopCondition = stopCondition;
        this.listeners = listeners;
        this.handle = handle;
        this.diagnostics = diagnostics;
    }

    @Override
    public RNG rng() {
        return rng;
    }

    @Override
    public double evaluate(S solution) {
        if (shouldStop())
            throw new SearchStoppedException();

        evaluations++;
        double cost = problem.cost(solution);
        if (cost < bestCost) {
            bestCost = cost;
            bestSolution = solution.copy();
            bestEvaluation = evaluations;
            bestIteration = iterations;
            timeToBestMillis = elapsedMillis();
            if (!listeners.isEmpty()) {
                ProgressSnapshot snapshot = snapshot();
                for (SearchListener l : listeners)
                    l.onImprovement(snapshot);
            }
        }
        return cost;
    }

    @Override
    public boolean shouldStop() {
        if (stopReason == null) {
            if (handle.isCancelled() || Thread.currentThread().isInterrupted())
                stopReason = StopReason.CANCELLED;
            else if (stopCondition.isSatisfied(this))
                stopReason = StopReason.CONDITION_MET;
        }
        return stopReason != null;
    }

    @Override
    public void iterationCompleted(Map<String, Double> metrics, List<? extends S> population) {
        if (diagnostics)
            metrics = withDiagnostics(metrics, population);
        iterations++;
        this.metrics = metrics.isEmpty() ? Collections.<String, Double>emptyMap() : Collections.unmodifiableMap(metrics);
        ProgressSnapshot snapshot = snapshot();
        handle.publish(snapshot);
        for (SearchListener l : listeners)
            l.onProgress(snapshot);
    }

    // the algorithm's metrics plus STAGNATION and, for a population of two or more, DUPLICATES and DIVERSITY
    private Map<String, Double> withDiagnostics(Map<String, Double> metrics, List<? extends S> population) {
        Map<String, Double> all = new LinkedHashMap<>(metrics);
        all.put(Metrics.STAGNATION, (double) (iterations - bestIteration));
        if (population.size() < 2)
            return all;

        all.put(Metrics.DUPLICATES, PopulationStats.duplicates(population));
        if (distanceDefined) {
            try {
                all.put(Metrics.DIVERSITY, PopulationStats.diversity(population));
            } catch (UnsupportedOperationException e) {
                distanceDefined = false;
            }
        }
        return all;
    }

    @Override
    public Progress progress() {
        return this;
    }

    @Override
    public S bestSolution() {
        return bestSolution == null ? null : bestSolution.copy();
    }

    ProgressSnapshot snapshot() {
        return new ProgressSnapshot(this, bestSolution);
    }

    // the algorithm returned normally: it either noticed the stop condition or finished by itself
    // the internal copy of the best solution; never modified, so it can be shared with a RunResult
    S bestSolutionInternal() {
        return bestSolution;
    }

    StopReason finalStopReason() {
        return stopReason != null ? stopReason : StopReason.ALGORITHM_FINISHED;
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
        return timeToBestMillis;
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
}
