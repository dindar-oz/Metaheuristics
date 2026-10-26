package problems.pecto.aco.gui;

import org.apache.commons.math3.random.MersenneTwister;
import util.random.RNG;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * The Mersenne Twister of {@link util.random.MersenneTwisterRNG}, giving the same numbers for the same seed, whose
 * state can be copied: a checkpoint keeps a copy, and a run restored from it draws exactly what it drew the first time.
 */
final class CheckpointRNG implements RNG {

    private final MersenneTwister mt;

    CheckpointRNG(long seed) {
        this(new MersenneTwister(seed));
    }

    private CheckpointRNG(MersenneTwister mt) {
        this.mt = mt;
    }

    @Override
    public int randInt(int max) {
        return mt.nextInt(max);
    }

    @Override
    public double randDouble() {
        return mt.nextDouble();
    }

    // an independent generator in the same state
    CheckpointRNG copy() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                out.writeObject(mt);
            }
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                return new CheckpointRNG((MersenneTwister) in.readObject());
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("cannot copy the random number generator", e);
        }
    }
}
