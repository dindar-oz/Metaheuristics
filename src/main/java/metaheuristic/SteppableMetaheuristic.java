package metaheuristic;

import base.OptimizationProblem;
import metaheuristic.run.SearchContext;
import representation.base.Representation;

/**
 * A metaheuristic whose run can be advanced one iteration at a time: {@link #start} sets up a run (its initial
 * solutions, evaluated through the context) and returns it as a {@link SearchProcess}. search() is then nothing but
 * stepping that process until it finishes or the stop condition holds, so a standalone run and a run stepped by a
 * scheduler are the same run. Like every Metaheuristic, the instance holds only configuration.
 */
public interface SteppableMetaheuristic<S extends Representation<S>, P extends OptimizationProblem<S>>
        extends Metaheuristic<S, P> {

    // a new run on the problem, set up but not yet stepped; it evaluates through, and stops with, the context
    SearchProcess start(P problem, SearchContext<S> ctx);

    @Override
    default void search(P problem, SearchContext<S> ctx) {
        try (SearchProcess run = start(problem, ctx)) {
            while (!ctx.shouldStop() && !run.finished())
                run.step();
        }
    }
}
