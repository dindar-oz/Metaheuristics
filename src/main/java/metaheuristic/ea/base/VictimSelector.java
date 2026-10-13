package metaheuristic.ea.base;

import representation.base.Representation;
import representation.base.Individual;
import util.random.RNG;

import java.util.List;

/**
 * Selects the individuals to be removed from the population. Must not modify the given list
 * and must keep no mutable state.
 */
public interface VictimSelector {
    <S extends Representation<S>> List<Individual<S>> selectVictims(List<Individual<S>> population, int victimCount, RNG rng);
}
