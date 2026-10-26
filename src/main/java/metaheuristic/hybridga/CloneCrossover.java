package metaheuristic.hybridga;

import metaheuristic.ea.base.CrossOverOperator;
import representation.base.Representation;
import util.random.RNG;

import java.util.Collections;
import java.util.List;

/**
 * No recombination: the child is one of the two parents, drawn with probability 1/2. Replacing the crossover with this
 * operator keeps the rest of the GA as it is (the same number of offspring, which are then mutated, refined and
 * inserted), so it measures what recombination itself contributes.
 */
public final class CloneCrossover<S extends Representation<S>> implements CrossOverOperator<S> {

    @Override
    public List<S> apply(S p1, S p2, RNG rng) {
        return Collections.singletonList((rng.randInt(2) == 0 ? p1 : p2).copy());
    }

    @Override
    public String toString() {
        return "clone";
    }
}
