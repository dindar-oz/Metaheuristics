package metaheuristic.rl;

import base.OptimizationProblem;
import representation.base.Representation;
import util.random.RNG;

/**
 * A problem whose solutions are built by a sequence of discrete decisions, seen as an episodic Markov decision process
 * by reinforcement learning metaheuristics: an episode builds one solution, a state is a partial solution, an action is
 * the next decision. The rewards of an episode must sum to minus the cost of the solution it builds times a positive
 * constant, so that maximizing the return is minimizing the cost.
 * Must be immutable like any OptimizationProblem: everything that changes during an episode lives in its Construction.
 */
public interface ConstructiveProblem<S extends Representation<S>> extends OptimizationProblem<S> {

    // a new, empty partial solution for one episode
    Construction<S> start(RNG rng);
}
