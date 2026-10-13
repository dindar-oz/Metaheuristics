package experiments;

import base.OptimizationProblem;
import metaheuristic.run.RunInfo;
import metaheuristic.run.RunResult;
import metaheuristic.run.Runner;
import metaheuristic.run.SearchListener;
import metaheuristic.termination.TerminalCondition;
import representation.base.Representation;
import util.random.Seeds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Runs every case repeatCount times, optionally in parallel:
 * <pre>
 * Experiment.of(cases).repeats(10).seed(42).stopWhen(new EvaluationBasedTC(50_000)).threads(8)
 *           .handler(new ConsoleERH()).run();
 * </pre>
 * Repeat r of case c always runs with seed Seeds.derive(seed, c, r), so results are reproducible regardless of
 * the number of threads or the scheduling order.
 */
public final class Experiment {

    private final List<ExperimentCase<?, ?>> cases;
    private int repeatCount = 1;
    private Long baseSeed = null;
    private TerminalCondition stopCondition = TerminalCondition.never();
    private int threads = 1;
    private final List<ExperimentResultHandler> handlers = new ArrayList<>();
    private final List<SearchListener> listeners = new ArrayList<>();
    private final List<Function<RunInfo, SearchListener>> listenerFactories = new ArrayList<>();

    private Experiment(List<? extends ExperimentCase<?, ?>> cases) {
        this.cases = new ArrayList<>(cases);
    }

    public static Experiment of(List<? extends ExperimentCase<?, ?>> cases) {
        return new Experiment(cases);
    }

    public static Experiment of(ExperimentCase<?, ?>... cases) {
        return new Experiment(Arrays.asList(cases));
    }

    public Experiment repeats(int repeatCount) {
        this.repeatCount = repeatCount;
        return this;
    }

    // Without a seed a random one is chosen and reported to the handlers
    public Experiment seed(long baseSeed) {
        this.baseSeed = baseSeed;
        return this;
    }

    public Experiment stopWhen(TerminalCondition stopCondition) {
        this.stopCondition = stopCondition;
        return this;
    }

    public Experiment threads(int threads) {
        this.threads = threads;
        return this;
    }

    public Experiment handler(ExperimentResultHandler handler) {
        handlers.add(handler);
        return this;
    }

    // attached to every run; called from the worker threads, so it must be thread-safe
    public Experiment listener(SearchListener listener) {
        listeners.add(listener);
        return this;
    }

    // a fresh listener for every run, e.g. info -> new CsvProgressListener(dir.resolve(info.seed() + ".csv"))
    public Experiment listenerPerRun(Function<RunInfo, SearchListener> factory) {
        listenerFactories.add(factory);
        return this;
    }

    // seed of repeat r of case c
    public static long seedOf(long baseSeed, int caseIndex, int repeat) {
        return Seeds.derive(baseSeed, caseIndex, repeat);
    }

    public void run() {
        long seed = baseSeed != null ? baseSeed : Seeds.random();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<List<Future<RunResult<?>>>> futures = new ArrayList<>();
            for (int c = 0; c < cases.size(); c++) {
                List<Future<RunResult<?>>> caseFutures = new ArrayList<>();
                for (int r = 0; r < repeatCount; r++) {
                    caseFutures.add(pool.submit(task(cases.get(c), seedOf(seed, c, r))));
                }
                futures.add(caseFutures);
            }

            handlers.forEach(h -> h.begin(seed));
            for (int c = 0; c < cases.size(); c++) {
                List<RunResult<?>> results = new ArrayList<>(repeatCount);
                for (Future<RunResult<?>> f : futures.get(c))
                    results.add(get(f));
                ExperimentCase<?, ?> experimentCase = cases.get(c);
                handlers.forEach(h -> h.handle(experimentCase, results));
            }
            handlers.forEach(ExperimentResultHandler::end);
        } finally {
            pool.shutdownNow();
        }
    }

    private <S extends Representation<S>, P extends OptimizationProblem<S>> Callable<RunResult<?>> task(
            ExperimentCase<S, P> experimentCase, long runSeed) {
        Runner<S, P> runner = experimentCase.runner().seed(runSeed).stopWhen(stopCondition);
        listeners.forEach(runner::listener);
        listenerFactories.forEach(runner::listenerPerRun);
        return () -> runner.run(experimentCase.getProblem());
    }

    private static RunResult<?> get(Future<RunResult<?>> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Experiment interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("A run failed", e.getCause());
        }
    }
}
