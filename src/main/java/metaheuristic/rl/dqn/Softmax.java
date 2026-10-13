package metaheuristic.rl.dqn;

import metaheuristic.Params;
import util.random.RNG;

/**
 * Boltzmann (softmax) exploration over standardized action values, with the temperature decaying geometrically from
 * start to end over decayEpisodes. Unlike epsilon-greedy, it explores mostly among the actions valued nearly as high
 * as the best, and it never takes an action valued far below the best one unless the temperature is high.
 */
final class Softmax implements Exploration {

    private final double start;
    private final double end;
    private final int decayEpisodes;

    Softmax(double start, double end, int decayEpisodes) {
        this.start = Params.positive("temperature start", start);
        this.end = Params.positive("temperature end", end);
        this.decayEpisodes = Params.positive("temperature decay episodes", decayEpisodes);
    }

    @Override
    public String metric() {
        return DQN.TEMPERATURE;
    }

    @Override
    public double level(int episode) {
        double progress = Math.min(1, episode / (double) decayEpisodes);
        return start * Math.pow(end / start, progress);
    }

    @Override
    public int choose(double[] values, double level, RNG rng) {
        int n = values.length;
        double max = Double.NEGATIVE_INFINITY, mean = 0;
        for (double v : values) {
            max = Math.max(max, v);
            mean += v / n;
        }
        double variance = 0;
        for (double v : values)
            variance += (v - mean) * (v - mean) / n;
        double scale = level * Math.sqrt(variance);
        if (!(scale > 0))                      // all values equal
            return rng.randInt(n);
        double[] weights = new double[n];
        for (int k = 0; k < n; k++)
            weights[k] = Math.exp((values[k] - max) / scale);
        return rng.rouletteSelect(weights);
    }

    @Override
    public String toString() {
        return "softmax(" + start + "->" + end + "/" + decayEpisodes + ")";
    }
}
