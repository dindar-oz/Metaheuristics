package metaheuristic.hybridga;

import metaheuristic.Params;
import metaheuristic.ea.base.ParentSelector;
import representation.base.Individual;
import representation.base.Representation;
import util.random.RNG;

import java.util.ArrayList;
import java.util.List;

/**
 * Roulette wheel selection of the research hybrid GA: parentCount distinct individuals, each drawn with probability
 * proportional to 1 / cost. Unlike {@link metaheuristic.ea.parentselector.RouletteWheelParentSelector}, which weighs
 * min + max - cost, the selection pressure does not depend on the spread of the costs. Costs must be non-negative.
 */
public final class InverseCostParentSelector implements ParentSelector {

    private static final double EPSILON = 1e-8;     // so that a zero cost gets a finite weight

    private final int parentCount;

    public InverseCostParentSelector(int parentCount) {
        this.parentCount = Params.positive("parentCount", parentCount);
    }

    @Override
    public <S extends Representation<S>> List<Individual<S>> selectParents(List<Individual<S>> population, RNG rng) {
        List<Individual<S>> candidates = new ArrayList<>(population);
        int count = Math.min(parentCount, candidates.size());
        List<Individual<S>> parents = new ArrayList<>(count);
        for (int c = 0; c < count; c++) {
            double[] weights = new double[candidates.size()];
            for (int i = 0; i < weights.length; i++)
                weights[i] = 1 / (candidates.get(i).getCost() + EPSILON);
            parents.add(candidates.remove(rng.rouletteSelect(weights)));
        }
        return parents;
    }

    @Override
    public String toString() {
        return "inverseCostRoulette(" + parentCount + ")";
    }
}
