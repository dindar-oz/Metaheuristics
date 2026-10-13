package metaheuristic.ea.base;

import representation.base.Representation;
import representation.base.Individual;
import util.random.RNG;

import java.util.List;

/**
 * Selects parents for crossover. Must not modify the given list and must keep no mutable state.
 */
public interface ParentSelector {
    <S extends Representation<S>> List<Individual<S>> selectParents(List<Individual<S>> population, RNG rng);
}
