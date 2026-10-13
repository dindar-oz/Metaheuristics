package metaheuristic.run;

/**
 * Observes a run. Callbacks are invoked synchronously on the run's thread, so they must be quick and must not
 * block; wrap slow listeners (GUI, file output) with metaheuristic.listener.Listeners.async() or .throttled().
 * All arguments are immutable and may be kept or handed to other threads.
 */
public interface SearchListener {

    default void onStart(RunInfo info) {
    }

    // after every iteration
    default void onProgress(ProgressSnapshot progress) {
    }

    // whenever a new best solution is found
    default void onImprovement(ProgressSnapshot progress) {
    }

    default void onFinish(RunResult<?> result) {
    }
}
