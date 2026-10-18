package metaheuristic.run;

import base.OptimizationProblem;
import metaheuristic.Metaheuristic;
import metaheuristic.termination.TerminalCondition;
import representation.base.Representation;
import util.random.MersenneTwisterRNG;
import util.random.Seeds;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Runs a metaheuristic on a problem:
 * <pre>
 * RunResult r = Runner.of(sa).seed(42).stopWhen(new EvaluationBasedTC(50_000)).listener(l).run(problem);
 * </pre>
 * Each run gets fresh state (RNG, counters, best solution), so one algorithm instance can be run many times,
 * also concurrently. Configure the Runner before running it; its setters are not thread-safe.
 */
public final class Runner<S extends Representation<S>, P extends OptimizationProblem<S>> {

    private final Metaheuristic<S, P> algorithm;
    private TerminalCondition stopCondition = TerminalCondition.never();
    private Long seed = null;
    private boolean diagnostics = false;
    private final List<SearchListener> listeners = new ArrayList<>();
    private final List<Function<RunInfo, SearchListener>> listenerFactories = new ArrayList<>();

    private Runner(Metaheuristic<S, P> algorithm) {
        this.algorithm = algorithm;
    }

    public static <S extends Representation<S>, P extends OptimizationProblem<S>> Runner<S, P> of(Metaheuristic<S, P> algorithm) {
        return new Runner<>(algorithm);
    }

    // Without a seed, a random one is chosen per run and reported in RunInfo/RunResult
    public Runner<S, P> seed(long seed) {
        this.seed = seed;
        return this;
    }

    // Adds the diagnostic metrics (Metrics.STAGNATION, DIVERSITY, DUPLICATES) to every iteration's metrics. They
    // cost a little time per iteration but never change the search; default off
    public Runner<S, P> diagnostics(boolean diagnostics) {
        this.diagnostics = diagnostics;
        return this;
    }

    public Runner<S, P> stopWhen(TerminalCondition stopCondition) {
        this.stopCondition = stopCondition;
        return this;
    }

    // The same instance observes every run of this Runner; it must be thread-safe if runs are concurrent
    public Runner<S, P> listener(SearchListener listener) {
        listeners.add(listener);
        return this;
    }

    // Creates a fresh listener for each run (e.g. one trace file per run); such listeners need no thread-safety
    public Runner<S, P> listenerPerRun(Function<RunInfo, SearchListener> factory) {
        listenerFactories.add(factory);
        return this;
    }

    // Runs on the calling thread. Interrupting the thread cancels the run.
    public RunResult<S> run(P problem) {
        return execute(problem, new RunHandle<>());
    }

    // Runs on the given executor; the handle can cancel it and poll its progress
    public RunHandle<S> start(P problem, Executor executor) {
        RunHandle<S> handle = new RunHandle<>();
        executor.execute(() -> {
            try {
                execute(problem, handle);
            } catch (RuntimeException | Error e) {
                // already reported through the handle's future
            }
        });
        return handle;
    }

    private RunResult<S> execute(P problem, RunHandle<S> handle) {
        long runSeed = seed != null ? seed : Seeds.random();
        RunInfo info = new RunInfo(algorithm.describe(), problem.getClass().getSimpleName(), runSeed);
        List<SearchListener> runListeners = new ArrayList<>(listeners);
        listenerFactories.forEach(f -> runListeners.add(f.apply(info)));
        SearchRun<S> run = new SearchRun<>(problem, new MersenneTwisterRNG(runSeed), stopCondition, runListeners, handle, diagnostics);

        try {
            runListeners.forEach(l -> l.onStart(info));
            try {
                algorithm.search(problem, run);
            } catch (SearchStoppedException stopped) {
                // the stop condition was reached inside an evaluation
            }
            ProgressSnapshot last = run.snapshot();
            handle.publish(last);
            RunResult<S> result = new RunResult<>(info, last, run.finalStopReason(), run.bestSolutionInternal());
            runListeners.forEach(l -> l.onFinish(result));
            handle.complete(result);
            return result;
        } catch (RuntimeException | Error e) {
            handle.fail(e);
            throw e;
        }
    }
}
