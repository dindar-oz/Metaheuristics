package metaheuristic.ea.base;

import metaheuristic.run.SearchContext;
import representation.base.Individual;
import representation.base.Population;
import representation.base.Representation;
import util.random.RNG;

import java.util.List;

/**
 * What the EA's strategies and hooks may use during one run. Confined to the run's thread.
 */
public interface EAContext<S extends Representation<S>> {

    SearchContext<S> search();

    default RNG rng() {
        return search().rng();
    }

    default boolean shouldStop() {
        return search().shouldStop();
    }

    // current generation, starting from 1
    long generation();

    // the target population size
    int populationSize();

    // evaluates the solution and wraps it into an individual born in the current generation
    Individual<S> evaluate(S solution);

    // a new random individual from the EA's InitialSolutionGenerator
    Individual<S> createRandom();

    // offers new individuals to the population through the EA's ReplacementStrategy
    void insert(Population<S> population, List<Individual<S>> newcomers);
}
