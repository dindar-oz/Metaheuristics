package metaheuristic.ea.base;

import representation.base.Representation;
import representation.base.Individual;
import representation.base.Population;

import java.util.List;

/**
 * Decides which newcomers enter the population and who leaves, so that the population
 * ends with at most ctx.populationSize() individuals. Must keep no mutable state.
 */
public interface ReplacementStrategy {
    <S extends Representation<S>> void replace(Population<S> population, List<Individual<S>> newcomers, EAContext<S> ctx);
}
