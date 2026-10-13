package metaheuristic.rl;

import representation.base.Representation;

/**
 * A Construction that describes each of its feasible actions by features (see FeaturedConstructiveProblem).
 */
public interface FeaturedConstruction<S extends Representation<S>> extends Construction<S> {

    // one row of featureCount() features per action of feasibleActions(), in the same order
    double[][] actionFeatures();

    /**
     * The exact reward of every action of feasibleActions(), in the same order, if the problem can tell it before the
     * action is taken; null if not (the default). Algorithms can then learn only what follows an action: e.g. DQN
     * learns Q(s, a) - reward(s, a), which starts as the greedy choice of the best immediate reward.
     */
    default double[] actionRewards() {
        return null;
    }

    /**
     * Used with actionRewards(): the scale of the value of what follows an action in the current state. A network
     * then learns (Q(s, a) - reward(s, a)) / valueScale(), so a problem whose values shrink or grow with its size
     * (e.g. a share of a total over n items) can keep what is learned independent of the size. Default: 1
     */
    default double valueScale() {
        return 1;
    }
}
