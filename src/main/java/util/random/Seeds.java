package util.random;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Deterministic seed derivation, so that e.g. repeat r of case c of an experiment always gets the same seed
 * no matter which thread runs it or in which order.
 */
public final class Seeds {

    private Seeds() {
    }

    // A fresh, unpredictable seed. Runs record the seed they used, so the run can still be reproduced.
    public static long random() {
        return ThreadLocalRandom.current().nextLong();
    }

    // Mixes the base seed with the given keys (SplitMix64 finalizer)
    public static long derive(long base, long... keys) {
        long h = mix(base);
        for (long key : keys)
            h = mix(h + 0x9E3779B97F4A7C15L + key);
        return h;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
