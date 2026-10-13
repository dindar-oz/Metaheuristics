package metaheuristic.rl.dqn;

import org.junit.jupiter.api.Test;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import static org.junit.jupiter.api.Assertions.*;

class ExplorationTest {

    private static final double[] VALUES = {0.10, 0.30, 0.25, -0.40, 0.29};

    @Test
    void schedulesDecayFromStartToEnd() {
        Exploration epsilon = Exploration.epsilonGreedy(1, 0.1, 100);
        assertEquals(1, epsilon.level(0), 1e-12);
        assertEquals(0.55, epsilon.level(50), 1e-12);
        assertEquals(0.1, epsilon.level(100), 1e-12);
        assertEquals(0.1, epsilon.level(1000), 1e-12);

        Exploration softmax = Exploration.softmax(1, 0.01, 100);
        assertEquals(1, softmax.level(0), 1e-12);
        assertEquals(0.1, softmax.level(50), 1e-12);      // geometric
        assertEquals(0.01, softmax.level(100), 1e-12);
        assertEquals(DQN.TEMPERATURE, softmax.metric());
        assertEquals(DQN.EPSILON, epsilon.metric());
    }

    @Test
    void zeroEpsilonAndColdSoftmaxAreGreedy() {
        RNG rng = new MersenneTwisterRNG(1);
        for (int i = 0; i < 100; i++) {
            assertEquals(1, Exploration.epsilonGreedy(1, 0, 1).choose(VALUES, 0, rng));
            assertEquals(1, Exploration.softmax(1, 1e-6, 1).choose(VALUES, 1e-6, rng));
        }
    }

    // values are standardized: scaling them does not change the choices
    @Test
    void softmaxIgnoresTheScaleOfTheValues() {
        Exploration softmax = Exploration.softmax(1, 0.1, 10);
        double[] scaled = new double[VALUES.length];
        for (int k = 0; k < VALUES.length; k++)
            scaled[k] = VALUES[k] * 1e-6 + 3;
        RNG a = new MersenneTwisterRNG(5), b = new MersenneTwisterRNG(5);
        for (int i = 0; i < 200; i++)
            assertEquals(softmax.choose(VALUES, 0.5, a), softmax.choose(scaled, 0.5, b));
    }

    // softmax prefers higher values and rarely takes the much worse action; equal values are chosen uniformly
    @Test
    void softmaxFavorsGoodActions() {
        Exploration softmax = Exploration.softmax(1, 0.1, 10);
        RNG rng = new MersenneTwisterRNG(2);
        int[] counts = new int[VALUES.length];
        for (int i = 0; i < 20000; i++)
            counts[softmax.choose(VALUES, 0.3, rng)]++;
        assertTrue(counts[1] > counts[4] && counts[4] > counts[2] && counts[2] > counts[0] && counts[0] > counts[3]);
        assertEquals(0, counts[3], 20);

        int[] uniform = new int[3];
        for (int i = 0; i < 3000; i++)
            uniform[softmax.choose(new double[]{0.5, 0.5, 0.5}, 0.3, rng)]++;
        for (int c : uniform)
            assertEquals(1000, c, 100);
    }
}
