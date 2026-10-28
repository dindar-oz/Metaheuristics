package metaheuristic;

import representation.base.Individual;
import representation.base.Representation;

import java.util.List;

/**
 * A {@link SearchProcess} that can take in solutions from other runs, already evaluated, without evaluating them
 * again. How it absorbs them is its own affair (e.g. replacing members of a population, or depositing pheromone);
 * the solutions must not be modified, as for any Individual.
 */
public interface Immigration<S extends Representation<S>> {

    void immigrate(List<Individual<S>> immigrants);

    /**
     * The solutions the run holds now (e.g. its population), so that immigrants can be chosen to differ from them;
     * empty if the run has none to show. Only read until the next step.
     */
    default List<S> residents() {
        return List.of();
    }
}
