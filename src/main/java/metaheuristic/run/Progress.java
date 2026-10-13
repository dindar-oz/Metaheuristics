package metaheuristic.run;

import java.util.Map;

/**
 * The state of a run as seen by stop conditions and listeners.
 * The live implementation is confined to the run's thread; ProgressSnapshot is the immutable, shareable copy.
 */
public interface Progress {

    long iterations();

    // number of objective function evaluations
    long evaluations();

    long elapsedMillis();

    // +Infinity before the first evaluation
    double bestCost();

    long timeToBestMillis();

    long evaluationsSinceImprovement();

    long iterationsSinceImprovement();

    // algorithm specific values reported with the last iteration (e.g. "temperature"); never null
    Map<String, Double> metrics();
}
