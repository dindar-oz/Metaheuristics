package util.random;

import java.security.SecureRandom;

/**
 * Created by dindar.oz on 7.01.2016.
 */
public class SecureRandomRNG implements RNG {
    static SecureRandom r = new SecureRandom();

    @Override
    public int randInt(int max) {
        return r.nextInt(max);
    }

    @Override
    public double randDouble() {
        return r.nextDouble();
    }
}
