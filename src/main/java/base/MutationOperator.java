package base;

import representation.base.Representation;
import util.random.RNG;

/**
 * Produces a neighbor of the given solution. Must not modify its input and must keep no mutable state,
 * so that one instance can be shared by concurrent runs. Operators that need problem data take the
 * problem in their constructor.
 */
@FunctionalInterface
public interface MutationOperator<S extends Representation<S>> {
    S apply(S solution, RNG rng);
}
