package metaheuristic.ea.victimselector;

import representation.base.Representation;
import metaheuristic.ea.base.VictimSelector;
import representation.base.Individual;
import util.random.RNG;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects the worst individuals.
 */
public class SimpleVictimSelector implements VictimSelector {
    @Override
    public <S extends Representation<S>> List<Individual<S>> selectVictims(List<Individual<S>> population, int victimCount, RNG rng) {
        List<Individual<S>> sorted = new ArrayList<>(population);
        sorted.sort(Individual.BY_COST.reversed());
        return new ArrayList<>(sorted.subList(0, Math.min(victimCount, sorted.size())));
    }

    @Override
    public String toString() {
        return "worst";
    }
}
