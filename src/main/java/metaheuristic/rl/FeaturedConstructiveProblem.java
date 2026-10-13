package metaheuristic.rl;

import representation.base.Representation;
import util.random.RNG;

/**
 * A ConstructiveProblem that describes every (state, action) pair by featureCount() numbers, for algorithms that
 * approximate action values by a learned function of them (e.g. DQN). Features should be roughly in [-1, 1].
 */
public interface FeaturedConstructiveProblem<S extends Representation<S>> extends ConstructiveProblem<S> {

    int featureCount();

    @Override
    FeaturedConstruction<S> start(RNG rng);
}
