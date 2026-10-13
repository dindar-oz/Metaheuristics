package metaheuristic;

import base.OptimizationProblem;
import metaheuristic.run.SearchContext;
import representation.base.Representation;

/**
 * A search algorithm for problems of type P with solutions of type S. Implementations hold only immutable configuration: all state of a run lives in local
 * variables of search() and in the SearchContext, so one instance can be run any number of times, also concurrently.
 * Run it with metaheuristic.run.Runner.
 */
public interface Metaheuristic<S extends Representation<S>, P extends OptimizationProblem<S>> {

    // Searches until ctx.shouldStop() or until the algorithm finishes by itself
    void search(P problem, SearchContext<S> ctx);

    String getName();

    // name and parameter values, recorded with every run for reproducibility
    default String describe() {
        return getName();
    }
}
