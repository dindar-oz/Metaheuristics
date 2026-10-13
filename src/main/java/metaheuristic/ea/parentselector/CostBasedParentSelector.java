package metaheuristic.ea.parentselector;

import representation.base.Representation;
import metaheuristic.ea.base.ParentSelector;
import representation.base.Individual;
import util.random.RNG;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects the parentCount best individuals (half of the population if parentCount is not given).
 */
public class CostBasedParentSelector implements ParentSelector {
    private final int parentCount; // <= 0 means half of the population

    public CostBasedParentSelector(int parentCount) {
        this.parentCount = parentCount;
    }

    public CostBasedParentSelector() {
        this(0);
    }

    @Override
    public <S extends Representation<S>> List<Individual<S>> selectParents(List<Individual<S>> population, RNG rng) {
        List<Individual<S>> sorted = new ArrayList<>(population);
        sorted.sort(Individual.BY_COST);
        int count = Math.min(parentCount > 0 ? parentCount : population.size() / 2, sorted.size());
        return new ArrayList<>(sorted.subList(0, count));
    }

    @Override
    public String toString() {
        return "best(" + (parentCount > 0 ? String.valueOf(parentCount) : "n/2") + ")";
    }
}
