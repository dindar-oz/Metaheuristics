package metaheuristic.ea.base;

import representation.base.Representation;
import representation.base.Individual;

import java.util.List;

/**
 * Decides how the crossover operators produce offsprings from the selected parents.
 * Offsprings are created with ctx.evaluate(); randomness comes from ctx.rng(). Must keep no mutable state.
 */
public interface CrossOverStrategy {
    <S extends Representation<S>> List<Individual<S>> generateOffsprings(List<Individual<S>> parents,
                                                                      List<CrossOverOperator<S>> crossOverOperators,
                                                                      EAContext<S> ctx);
}
