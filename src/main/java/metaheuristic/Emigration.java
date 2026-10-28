package metaheuristic;

import representation.base.Individual;
import representation.base.Representation;

import java.util.List;

/**
 * A {@link SearchProcess} that can offer solutions to other runs. It offers what it has at hand, already evaluated
 * (e.g. the population, or the ants of the last iteration); the scheduler chooses among them. Offering changes nothing
 * in the run.
 */
public interface Emigration<S extends Representation<S>> {

    // the solutions the run can give away now, with their costs; possibly empty, never null
    List<Individual<S>> emigrants();
}
