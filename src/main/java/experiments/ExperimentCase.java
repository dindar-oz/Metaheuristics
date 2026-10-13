package experiments;

import base.OptimizationProblem;
import metaheuristic.Metaheuristic;
import metaheuristic.run.Runner;
import representation.base.Representation;

/**
 * One algorithm-problem pair of an experiment. The algorithm is only configuration, so its repeats
 * can run concurrently.
 */
public final class ExperimentCase<S extends Representation<S>, P extends OptimizationProblem<S>> {
    private final String title;
    private final Metaheuristic<S, P> algorithm;
    private final P problem;

    public static <S extends Representation<S>, P extends OptimizationProblem<S>> ExperimentCase<S, P> of(
            String title, Metaheuristic<S, P> algorithm, P problem) {
        return new ExperimentCase<>(title, algorithm, problem);
    }

    public ExperimentCase(String title, Metaheuristic<S, P> algorithm, P problem) {
        this.title = title;
        this.algorithm = algorithm;
        this.problem = problem;
    }

    public String getTitle() {
        return title;
    }

    public Metaheuristic<S, P> getAlgorithm() {
        return algorithm;
    }

    public P getProblem() {
        return problem;
    }

    // a Runner for this case; the caller adds seed, stop condition and listeners
    public Runner<S, P> runner() {
        return Runner.of(algorithm);
    }

    @Override
    public String toString() {
        return title;
    }
}
