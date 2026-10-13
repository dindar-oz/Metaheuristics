package metaheuristic.ea.base;

import representation.base.Representation;
import util.random.RNG;

import java.util.List;

/**
 * Produces offsprings from two parents without modifying them. Must keep no mutable state.
 */
public interface CrossOverOperator<S extends Representation<S>> {
    List<S> apply(S p1, S p2, RNG rng);
}
