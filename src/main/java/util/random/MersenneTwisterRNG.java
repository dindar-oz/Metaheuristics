package util.random;

import org.apache.commons.math3.random.MersenneTwister;

/**
 * Fast, seedable RNG. Not thread-safe: use one instance per run.
 */
public class MersenneTwisterRNG implements RNG {
    private final MersenneTwister mt;

    public MersenneTwisterRNG(long seed) {
        mt = new MersenneTwister(seed);
    }

    @Override
    public int randInt(int max) {
        return mt.nextInt(max);
    }

    @Override
    public double randDouble() {
        return mt.nextDouble();
    }
}
