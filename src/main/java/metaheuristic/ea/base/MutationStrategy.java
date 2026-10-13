package metaheuristic.ea.base;

import representation.base.Representation;
import base.MutationOperator;
import representation.base.Population;

import java.util.List;

/**
 * Decides how the mutation operators are applied to a population.
 * Mutants are created with ctx.evaluate(); randomness comes from ctx.rng(). Must keep no mutable state.
 */
public interface MutationStrategy {
    <S extends Representation<S>> void applyMutations(Population<S> population, List<MutationOperator<S>> mutationOperators,
                                                        EAContext<S> ctx);
}
