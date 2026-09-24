package base;

import representation.base.Representation;

/**
 * A minimization problem: every algorithm tries to find the representation with the lowest cost.
 */
public interface OptimizationProblem {

    // returns the objective value of the given solution
    double cost(Representation r);

    // returns if the given solution is feasible
    default boolean isFeasible(Representation r) {
        return true;
    }
}
