package metaheuristic.ea.parentselector;

import representation.base.Representation;
import metaheuristic.ea.base.ParentSelector;
import representation.base.Individual;
import util.random.RNG;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects parentCount distinct individuals; lower cost means higher selection probability.
 */
public class RouletteWheelParentSelector implements ParentSelector {
    private final int parentCount; // <= 0 means half of the population

    public RouletteWheelParentSelector(int parentCount) {
        this.parentCount = parentCount;
    }

    public RouletteWheelParentSelector() {
        this(0);
    }

    @Override
    public <S extends Representation<S>> List<Individual<S>> selectParents(List<Individual<S>> population, RNG rng) {
        List<Individual<S>> candidates = new ArrayList<>(population);
        int count = Math.min(parentCount > 0 ? parentCount : population.size() / 2, candidates.size());
        List<Individual<S>> parents = new ArrayList<>(count);
        for (int c = 0; c < count; c++) {
            double[] costs = new double[candidates.size()];
            for (int i = 0; i < costs.length; i++)
                costs[i] = candidates.get(i).getCost();
            parents.add(candidates.remove(rng.rouletteSelectInverse(costs)));
        }
        return parents;
    }

    @Override
    public String toString() {
        return "roulette(" + (parentCount > 0 ? String.valueOf(parentCount) : "n/2") + ")";
    }
}
