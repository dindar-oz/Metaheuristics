package metaheuristic.ea.base;

import representation.base.Representation;
import representation.base.Population;

/**
 * Extra behaviour applied at the end of every EA generation (immigration, ageing, local search, ...).
 * Must keep no mutable state: everything a hook needs about the run comes from the EAContext.
 */
public interface GenerationHook<S extends Representation<S>> {
    void afterGeneration(Population<S> population, EAContext<S> ctx);
}
