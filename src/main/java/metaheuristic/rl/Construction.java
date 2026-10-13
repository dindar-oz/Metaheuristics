package metaheuristic.rl;

import representation.base.Representation;

/**
 * One episode of a ConstructiveProblem: a partial solution that grows by one decision (action) per step.
 * Mutable and confined to the thread of its run.
 */
public interface Construction<S extends Representation<S>> {

    // true when no more decisions can be made; solution() is then the complete solution
    boolean isComplete();

    // the actions possible in the current state; empty when complete
    int[] feasibleActions();

    // applies one of the feasible actions and returns its reward
    double step(int action);

    // the solution built so far
    S solution();
}
