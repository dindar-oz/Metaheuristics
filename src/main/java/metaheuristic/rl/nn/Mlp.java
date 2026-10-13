package metaheuristic.rl.nn;

import util.random.RNG;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A fully connected network with ReLU hidden layers and one linear output, to approximate a scalar function such as
 * an action value. An input is a row of a float array, so that many inputs can be stored compactly.
 * Not thread-safe: forward() keeps the activations that the following backward() uses.
 */
public final class Mlp {

    final int[] sizes;          // inputs, hidden layers..., 1
    // layer l: sizes[l + 1] rows of sizes[l] columns, column-major (weight of input c to unit r at c * rows + r),
    // so that the inner loops are vectorizable updates of a whole layer rather than sums
    final double[][] weights;
    final double[][] biases;
    private final double[][] outputs;   // outputs of each layer in the last forward(), after the activation
    private final double[][] deltas;    // scratch of backward()

    // He-initialized weights for the ReLU layers, a smaller uniform range for the output layer, zero biases
    public Mlp(int inputs, int[] hidden, RNG rng) {
        this(inputs, hidden, rng, 1);
    }

    /**
     * The same with the initial output weights scaled by outputScale; 0 makes the network output 0 until it is
     * trained (the hidden layers start learning once the output weights have moved).
     */
    public Mlp(int inputs, int[] hidden, RNG rng, double outputScale) {
        if (inputs < 1)
            throw new IllegalArgumentException("inputs must be positive");
        sizes = new int[hidden.length + 2];
        sizes[0] = inputs;
        for (int l = 0; l < hidden.length; l++) {
            if (hidden[l] < 1)
                throw new IllegalArgumentException("hidden layer sizes must be positive");
            sizes[l + 1] = hidden[l];
        }
        sizes[sizes.length - 1] = 1;

        int layers = sizes.length - 1;
        weights = new double[layers][];
        biases = new double[layers][];
        for (int l = 0; l < layers; l++) {
            weights[l] = new double[sizes[l + 1] * sizes[l]];
            biases[l] = new double[sizes[l + 1]];
            double bound = l < layers - 1 ? Math.sqrt(6.0 / sizes[l]) : outputScale * Math.sqrt(1.0 / sizes[l]);
            for (int k = 0; k < weights[l].length; k++)
                weights[l][k] = rng.randDouble(-bound, bound);
        }
        outputs = scratch(sizes);
        deltas = scratch(sizes);
    }

    // zero parameters of the given layer sizes
    private Mlp(int[] sizes) {
        if (sizes.length < 2 || sizes[sizes.length - 1] != 1)
            throw new IllegalArgumentException("invalid layer sizes " + java.util.Arrays.toString(sizes));
        for (int size : sizes)
            if (size < 1)
                throw new IllegalArgumentException("invalid layer sizes " + java.util.Arrays.toString(sizes));
        this.sizes = sizes.clone();
        int layers = sizes.length - 1;
        weights = new double[layers][];
        biases = new double[layers][];
        for (int l = 0; l < layers; l++) {
            weights[l] = new double[sizes[l + 1] * sizes[l]];
            biases[l] = new double[sizes[l + 1]];
        }
        outputs = scratch(sizes);
        deltas = scratch(sizes);
    }

    private Mlp(Mlp other) {
        sizes = other.sizes.clone();
        weights = new double[other.weights.length][];
        biases = new double[other.biases.length][];
        for (int l = 0; l < weights.length; l++) {
            weights[l] = other.weights[l].clone();
            biases[l] = other.biases[l].clone();
        }
        outputs = scratch(sizes);
        deltas = scratch(sizes);
    }

    private static double[][] scratch(int[] sizes) {
        double[][] s = new double[sizes.length - 1][];
        for (int l = 0; l < s.length; l++)
            s[l] = new double[sizes[l + 1]];
        return s;
    }

    public Mlp copy() {
        return new Mlp(this);
    }

    // sets the parameters to those of a network of the same shape
    public void copyFrom(Mlp other) {
        if (!java.util.Arrays.equals(sizes, other.sizes))
            throw new IllegalArgumentException("networks of different shapes");
        for (int l = 0; l < weights.length; l++) {
            System.arraycopy(other.weights[l], 0, weights[l], 0, weights[l].length);
            System.arraycopy(other.biases[l], 0, biases[l], 0, biases[l].length);
        }
    }

    public int inputCount() {
        return sizes[0];
    }

    // inputs, hidden layer sizes..., 1
    public int[] layerSizes() {
        return sizes.clone();
    }

    public boolean hasSameParameters(Mlp other) {
        return java.util.Arrays.equals(sizes, other.sizes) && java.util.Arrays.deepEquals(weights, other.weights)
                && java.util.Arrays.deepEquals(biases, other.biases);
    }

    // ---- persistence: a magic number, the layer sizes, then the weights and biases of every layer, exactly

    private static final int MAGIC = 0x4D4C5031;   // "MLP1"

    public void write(DataOutput out) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(sizes.length);
        for (int size : sizes)
            out.writeInt(size);
        for (int l = 0; l < weights.length; l++) {
            for (double w : weights[l])
                out.writeDouble(w);
            for (double b : biases[l])
                out.writeDouble(b);
        }
    }

    public static Mlp read(DataInput in) throws IOException {
        if (in.readInt() != MAGIC)
            throw new IOException("not a saved network");
        int count = in.readInt();
        if (count < 2 || count > 1000)
            throw new IOException("invalid number of layers: " + count);
        int[] sizes = new int[count];
        for (int k = 0; k < count; k++)
            sizes[k] = in.readInt();
        Mlp net;
        try {
            net = new Mlp(sizes);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage());
        }
        for (int l = 0; l < net.weights.length; l++) {
            for (int k = 0; k < net.weights[l].length; k++)
                net.weights[l][k] = in.readDouble();
            for (int k = 0; k < net.biases[l].length; k++)
                net.biases[l][k] = in.readDouble();
        }
        return net;
    }

    public void save(Path file) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            write(out);
        }
    }

    public static Mlp load(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            return read(in);
        }
    }

    // the output for the input x[offset .. offset + inputCount() - 1]
    public double forward(float[] x, int offset) {
        int layers = weights.length;
        for (int l = 0; l < layers; l++) {
            int rows = sizes[l + 1], cols = sizes[l];
            double[] w = weights[l], out = outputs[l];
            double[] previous = l == 0 ? null : outputs[l - 1];
            System.arraycopy(biases[l], 0, out, 0, rows);
            for (int c = 0; c < cols; c++) {
                double input = previous == null ? x[offset + c] : previous[c];
                if (input == 0)
                    continue;
                int base = c * rows;
                for (int r = 0; r < rows; r++)
                    out[r] += w[base + r] * input;
            }
            if (l < layers - 1)
                for (int r = 0; r < rows; r++)
                    out[r] = Math.max(0, out[r]);
        }
        return outputs[layers - 1][0];
    }

    /**
     * Adds outputGradient times the gradient of the output with respect to every parameter to g, at the input of
     * the last forward() call, which must be the same x and offset.
     */
    public void backward(float[] x, int offset, double outputGradient, Gradients g) {
        int layers = weights.length;
        deltas[layers - 1][0] = outputGradient;
        for (int l = layers - 1; l >= 0; l--) {
            int rows = sizes[l + 1], cols = sizes[l];
            double[] w = weights[l], delta = deltas[l], gw = g.weights[l], gb = g.biases[l];
            double[] previous = l == 0 ? null : outputs[l - 1];
            for (int r = 0; r < rows; r++)
                gb[r] += delta[r];
            for (int c = 0; c < cols; c++) {
                double input = previous == null ? x[offset + c] : previous[c];
                if (input == 0)
                    continue;
                int base = c * rows;
                for (int r = 0; r < rows; r++)
                    gw[base + r] += delta[r] * input;
            }
            if (l > 0) {
                double[] below = deltas[l - 1];
                for (int c = 0; c < cols; c++) {
                    if (previous[c] <= 0) {     // ReLU derivative
                        below[c] = 0;
                        continue;
                    }
                    double sum = 0;
                    int base = c * rows;
                    for (int r = 0; r < rows; r++)
                        sum += w[base + r] * delta[r];
                    below[c] = sum;
                }
            }
        }
    }

    public Gradients newGradients() {
        return new Gradients(this);
    }

    /**
     * Accumulated gradients of a network's parameters, in the same shapes.
     */
    public static final class Gradients {
        final double[][] weights;
        final double[][] biases;

        private Gradients(Mlp net) {
            weights = new double[net.weights.length][];
            biases = new double[net.biases.length][];
            for (int l = 0; l < weights.length; l++) {
                weights[l] = new double[net.weights[l].length];
                biases[l] = new double[net.biases[l].length];
            }
        }

        public void clear() {
            for (int l = 0; l < weights.length; l++) {
                java.util.Arrays.fill(weights[l], 0);
                java.util.Arrays.fill(biases[l], 0);
            }
        }

        public double norm() {
            double sum = 0;
            for (int l = 0; l < weights.length; l++) {
                for (double v : weights[l])
                    sum += v * v;
                for (double v : biases[l])
                    sum += v * v;
            }
            return Math.sqrt(sum);
        }
    }
}
