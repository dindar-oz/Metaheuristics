package base;

import representation.base.Representation;

/**
 * A minimization problem over solutions of type S. Must be immutable (or at least side-effect free when
 * computing costs), because concurrent runs may share one instance.
 */
public interface OptimizationProblem<S extends Representation<S>> {

    // returns the objective value of the given solution
    double cost(S solution);

    // returns if the given solution is feasible
    default boolean isFeasible(S solution) {
        return true;
    }
}
