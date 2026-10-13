package metaheuristic.run;

import representation.base.Representation;

import java.util.Map;

/**
 * Immutable copy of a run's progress. Safe to hand to other threads (listeners, GUI).
 */
public final class ProgressSnapshot implements Progress {
    private final long iterations;
    private final long evaluations;
    private final long elapsedMillis;
    private final double bestCost;
    private final long timeToBestMillis;
    private final long evaluationsSinceImprovement;
    private final long iterationsSinceImprovement;
    private final Map<String, Double> metrics;
    private final Representation<?> bestSolution; // never modified; copied on access

    ProgressSnapshot(Progress p, Representation<?> bestSolution) {
        this.iterations = p.iterations();
        this.evaluations = p.evaluations();
        this.elapsedMillis = p.elapsedMillis();
        this.bestCost = p.bestCost();
        this.timeToBestMillis = p.timeToBestMillis();
        this.evaluationsSinceImprovement = p.evaluationsSinceImprovement();
        this.iterationsSinceImprovement = p.iterationsSinceImprovement();
        this.metrics = p.metrics(); // already immutable
        this.bestSolution = bestSolution;
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
        return elapsedMillis;
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
        return evaluationsSinceImprovement;
    }

    @Override
    public long iterationsSinceImprovement() {
        return iterationsSinceImprovement;
    }

    @Override
    public Map<String, Double> metrics() {
        return metrics;
    }

    // a copy of the best solution found so far, or null before the first evaluation
    public Representation<?> bestSolution() {
        return bestSolution == null ? null : bestSolution.copy();
    }

    @Override
    public String toString() {
        return String.format("it=%d evals=%d t=%dms best=%.6f%s", iterations, evaluations, elapsedMillis, bestCost,
                metrics.isEmpty() ? "" : " " + metrics);
    }
}
