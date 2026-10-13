package experiments;

import metaheuristic.run.RunResult;

import java.util.List;

/**
 * Receives the results of an experiment. All callbacks are made from the thread that called Experiment.run(),
 * in case order, so implementations need not be thread-safe.
 */
public interface ExperimentResultHandler {

    default void begin(long baseSeed) {
    }

    // all repeats of one case, in repeat order
    void handle(ExperimentCase<?, ?> experimentCase, List<RunResult<?>> runs);

    default void end() {
    }
}
