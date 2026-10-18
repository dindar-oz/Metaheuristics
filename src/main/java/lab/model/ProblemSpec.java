package lab.model;

import base.OptimizationProblem;
import lab.view.SolutionVisualizer;
import metaheuristic.Metaheuristic;
import metaheuristic.run.ProgressSnapshot;
import metaheuristic.run.RunHandle;
import metaheuristic.run.RunResult;
import metaheuristic.run.Runner;
import metaheuristic.run.SearchListener;
import metaheuristic.termination.TerminalCondition;
import representation.base.Representation;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * A problem offered by the lab: its parameters, how to create an instance, and the algorithms that can solve it.
 * The generic types stay inside this class; the UI only works with names, ParamValues and Sessions.
 */
public final class ProblemSpec<S extends Representation<S>, P extends OptimizationProblem<S>> implements LabProblem {
    private final String name;
    private final List<ParamSpec> params;
    private final Function<ParamValues, P> factory;
    private final List<AlgorithmSpec<S, P>> algorithms;
    private final SolutionVisualizer<S, P> visualizer;

    public ProblemSpec(String name, List<ParamSpec> params, Function<ParamValues, P> factory, List<AlgorithmSpec<S, P>> algorithms) {
        this(name, params, factory, algorithms, SolutionVisualizer.<S, P>text());
    }

    public ProblemSpec(String name, List<ParamSpec> params, Function<ParamValues, P> factory, List<AlgorithmSpec<S, P>> algorithms,
                       SolutionVisualizer<S, P> visualizer) {
        this.name = name;
        this.params = params;
        this.factory = factory;
        this.algorithms = algorithms;
        this.visualizer = visualizer;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<ParamSpec> params() {
        return params;
    }

    @Override
    public List<String> algorithmNames() {
        List<String> names = new ArrayList<>();
        for (AlgorithmSpec<S, P> a : algorithms)
            names.add(a.name());
        return names;
    }

    @Override
    public List<ParamSpec> algorithmParams(String algorithm) {
        return algorithm(algorithm).params();
    }

    // Creates the problem instance; runs started from the session share it (problems are immutable)
    @Override
    public Session newSession(ParamValues problemParams) {
        P problem = factory.apply(problemParams);
        String description = name + " (" + problemParams + ")";
        return new Session() {
            @Override
            public String describe() {
                return description;
            }

            @Override
            public StartedRun start(String algorithm, ParamValues values, long seed, TerminalCondition stop,
                                    SearchListener listener, Executor executor) {
                RunHandle<S> handle = startRun(algorithm(algorithm).create(problem, values), problem, seed, stop, listener, executor);
                return new StartedRun() {
                    @Override
                    public RunHandle<?> handle() {
                        return handle;
                    }

                    @Override
                    public boolean hasSolution() {
                        return bestSolution() != null;
                    }

                    @Override
                    public JComponent viewBestSolution() {
                        S best = bestSolution();
                        if (best == null)
                            throw new IllegalStateException("the run has no solution yet");
                        return visualizer.view(problem, best);
                    }

                    // the final best solution, or the best so far while the run is going
                    @SuppressWarnings("unchecked") // snapshots of this run only ever hold solutions of type S
                    private S bestSolution() {
                        if (handle.isDone() && !handle.result().isCompletedExceptionally()) {
                            RunResult<S> result = handle.result().join();
                            return result.bestSolution();
                        }
                        ProgressSnapshot p = handle.latestProgress();
                        return p == null ? null : (S) p.bestSolution();
                    }
                };
            }
        };
    }

    private static <S extends Representation<S>, Q extends OptimizationProblem<S>> RunHandle<S> startRun(
            Metaheuristic<S, Q> algorithm, Q problem, long seed, TerminalCondition stop, SearchListener listener, Executor executor) {
        return Runner.of(algorithm).seed(seed).stopWhen(stop).diagnostics(true).listener(listener).start(problem, executor);
    }

    private AlgorithmSpec<S, P> algorithm(String name) {
        for (AlgorithmSpec<S, P> a : algorithms)
            if (a.name().equals(name))
                return a;
        throw new IllegalArgumentException("Unknown algorithm " + name);
    }

    @Override
    public String toString() {
        return name;
    }
}
