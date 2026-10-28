package metaheuristic.island;

import metaheuristic.run.PopulationStats;
import metaheuristic.run.Progress;
import metaheuristic.run.SearchContext;
import representation.base.Individual;
import representation.base.Representation;
import util.random.RNG;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The search context of one island: every evaluation goes through the run's context (so the run counts it, keeps the
 * overall best and stops), while the island keeps its own RNG, counters, best solution and last metrics. Its best
 * also counts the migrants it received, which it did not evaluate. Confined to the run's thread.
 */
final class IslandContext<S extends Representation<S>> implements SearchContext<S>, Progress {

    private final SearchContext<S> run;
    private final RNG rng;

    private long iterations = 0;
    private long evaluations = 0;
    private double bestCost = Double.POSITIVE_INFINITY;
    private S bestSolution = null;          // private copy, replaced (never modified) on improvement
    private long bestEvaluation = 0;
    private long bestIteration = 0;
    private long timeToBestMillis = 0;
    private Map<String, Double> metrics = Collections.emptyMap();

    // the population of the island's last observed iteration
    private boolean observe = false;
    private boolean distanceDefined = true;
    private double diversity = Double.NaN, duplicates = Double.NaN;

    IslandContext(SearchContext<S> run, RNG rng) {
        this.run = run;
        this.rng = rng;
    }

    @Override
    public RNG rng() {
        return rng;
    }

    @Override
    public double evaluate(S solution) {
        double cost = run.evaluate(solution);
        evaluations++;
        note(solution, cost);
        return cost;
    }

    // the migrants the island took in count for its best, but not as its evaluations
    void received(List<Individual<S>> migrants) {
        for (Individual<S> m : migrants)
            note(m.getRepresentation(), m.getCost());
    }

    private void note(S solution, double cost) {
        if (cost < bestCost) {
            bestCost = cost;
            bestSolution = solution.copy();
            bestEvaluation = evaluations;
            bestIteration = iterations;
            timeToBestMillis = elapsedMillis();
        }
    }

    @Override
    public boolean shouldStop() {
        return run.shouldStop();
    }

    // whether the next iterations' populations are measured (diversity, duplicates); off by default, as it costs time
    void observe(boolean observe) {
        this.observe = observe;
    }

    @Override
    public void iterationCompleted(Map<String, Double> metrics, List<? extends S> population) {
        iterations++;
        // in the algorithm's order (Map.copyOf would not keep it)
        this.metrics = metrics.isEmpty() ? Collections.<String, Double>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
        if (observe && population.size() >= 2) {
            duplicates = PopulationStats.duplicates(population);
            if (distanceDefined) {
                try {
                    diversity = PopulationStats.diversity(population);
                } catch (UnsupportedOperationException e) {
                    distanceDefined = false;
                }
            }
        }
    }

    // of the last observed iteration's population; NaN if none was observed
    double diversity() {
        return diversity;
    }

    double duplicates() {
        return duplicates;
    }

    @Override
    public Progress progress() {
        return this;
    }

    @Override
    public S bestSolution() {
        return bestSolution == null ? null : bestSolution.copy();
    }

    @Override
    public long iterations() {
        return iterations;
    }

    @Override
    public long evaluations() {
        return evaluations;
    }

    // of the whole run: the islands share the clock
    @Override
    public long elapsedMillis() {
        return run.progress().elapsedMillis();
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
