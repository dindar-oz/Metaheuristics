package metaheuristic.rl.dqn;

import util.random.RNG;

/**
 * How DQN chooses actions while learning: from the values the online network gives the feasible actions of a state,
 * at an exploration level (epsilon, temperature) that changes with the episode. Implementations must be immutable.
 */
public interface Exploration {

    // name of the metric that reports the level
    String metric();

    // the exploration level in the given episode (0-based)
    double level(int episode);

    // the index of the action to take, given the values of the feasible actions
    int choose(double[] values, double level, RNG rng);

    // a uniformly random action with probability epsilon, otherwise the best; epsilon decays linearly
    static Exploration epsilonGreedy(double start, double end, int decayEpisodes) {
        return new EpsilonGreedy(start, end, decayEpisodes);
    }

    /**
     * Boltzmann exploration: action a with probability proportional to exp((Q(a) - max Q) / (temperature * sd)),
     * where sd is the standard deviation of the values in the state, so that the temperature does not depend on the
     * scale of the values. The temperature decays geometrically.
     */
    static Exploration softmax(double start, double end, int decayEpisodes) {
        return new Softmax(start, end, decayEpisodes);
    }
}
