package metaheuristic.run;

import representation.base.Representation;
import util.random.RNG;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Per-run services handed to Metaheuristic.search(). Confined to the run's thread: algorithms must not share
 * it (or its RNG) with other threads.
 */
public interface SearchContext<S extends Representation<S>> {

    // the run's own seeded RNG; the only source of randomness an algorithm may use
    RNG rng();

    /**
     * Evaluates the solution, counts the evaluation and tracks the best solution.
     * Once the stop condition holds, no more evaluations are performed: this throws SearchStoppedException,
     * which the Runner handles. Algorithms normally leave their loops earlier by checking shouldStop().
     */
    double evaluate(S solution);

    // true when the stop condition holds, or the run was cancelled or interrupted
    boolean shouldStop();

    /**
     * Marks the end of an iteration and publishes progress with the given algorithm specific metrics. Population
     * based algorithms also pass the solutions of the iteration (e.g. the ants' solutions or the population), from
     * which the diagnostic metrics DIVERSITY and DUPLICATES are derived; they are only read during the call.
     */
    void iterationCompleted(Map<String, Double> metrics, List<? extends S> population);

    default void iterationCompleted(Map<String, Double> metrics) {
        iterationCompleted(metrics, Collections.emptyList());
    }

    default void iterationCompleted() {
        iterationCompleted(Collections.emptyMap());
    }

    Progress progress();

    // a copy of the best solution so far (null before the first evaluation)
    S bestSolution();
}
