package metaheuristic.rl.dqn;

import metaheuristic.Params;
import util.random.RNG;

/**
 * Epsilon-greedy exploration with epsilon decaying linearly from start to end over decayEpisodes.
 */
final class EpsilonGreedy implements Exploration {

    private final double start;
    private final double end;
    private final int decayEpisodes;

    EpsilonGreedy(double start, double end, int decayEpisodes) {
        this.start = Params.probability("epsilon start", start);
        this.end = Params.probability("epsilon end", end);
        this.decayEpisodes = Params.positive("epsilon decay episodes", decayEpisodes);
    }

    @Override
    public String metric() {
        return DQN.EPSILON;
    }

    @Override
    public double level(int episode) {
        double progress = Math.min(1, episode / (double) decayEpisodes);
        return start + (end - start) * progress;
    }

    @Override
    public int choose(double[] values, double level, RNG rng) {
        return rng.rollDice(level) ? rng.randInt(values.length) : DQN.argmax(values);
    }

    @Override
    public String toString() {
        return "epsilon-greedy(" + start + "->" + end + "/" + decayEpisodes + ")";
    }
}
