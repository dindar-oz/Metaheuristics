package metaheuristic.rl.nn;

import org.junit.jupiter.api.Test;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import static org.junit.jupiter.api.Assertions.*;

class MlpTest {

    // backward() must match central finite differences of forward() for every parameter
    @Test
    void gradientsMatchFiniteDifferences() {
        RNG rng = new MersenneTwisterRNG(3);
        Mlp net = new Mlp(4, new int[]{6, 5}, rng);
        float[] x = {0.3f, -0.7f, 0.5f, 0.9f, 0.1f};   // input at offset 1
        Mlp.Gradients g = net.newGradients();
        net.forward(x, 1);
        net.backward(x, 1, 1.0, g);

        double h = 1e-6;
        for (int l = 0; l < net.weights.length; l++) {
            for (int k = 0; k < net.weights[l].length; k++)
                assertEquals(numeric(net, x, net.weights[l], k, h), g.weights[l][k], 1e-6, "weight " + l + "/" + k);
            for (int k = 0; k < net.biases[l].length; k++)
                assertEquals(numeric(net, x, net.biases[l], k, h), g.biases[l][k], 1e-6, "bias " + l + "/" + k);
        }
    }

    private static double numeric(Mlp net, float[] x, double[] p, int k, double h) {
        double saved = p[k];
        p[k] = saved + h;
        double plus = net.forward(x, 1);
        p[k] = saved - h;
        double minus = net.forward(x, 1);
        p[k] = saved;
        return (plus - minus) / (2 * h);
    }

    @Test
    void copiesAreIndependent() {
        Mlp net = new Mlp(2, new int[]{3}, new MersenneTwisterRNG(1));
        float[] x = {0.5f, -0.25f};
        double before = net.forward(x, 0);
        Mlp copy = net.copy();
        copy.biases[1][0] += 1;
        assertEquals(before, net.forward(x, 0));
        assertNotEquals(before, copy.forward(x, 0));
        copy.copyFrom(net);
        assertEquals(before, copy.forward(x, 0));
    }

    @Test
    void savedNetworksLoadExactly() throws Exception {
        Mlp net = new Mlp(3, new int[]{5, 4}, new MersenneTwisterRNG(9));
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        net.write(new java.io.DataOutputStream(bytes));
        Mlp loaded = Mlp.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        assertTrue(loaded.hasSameParameters(net));
        assertArrayEquals(new int[]{3, 5, 4, 1}, loaded.layerSizes());
        float[] x = {0.2f, -0.4f, 0.9f};
        assertEquals(net.forward(x, 0), loaded.forward(x, 0));

        assertThrows(java.io.IOException.class, () ->
                Mlp.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}))));
    }

    // Adam on the mean squared error must fit a simple nonlinear function
    @Test
    void adamFitsAFunction() {
        RNG rng = new MersenneTwisterRNG(7);
        Mlp net = new Mlp(2, new int[]{16, 16}, rng);
        Adam adam = new Adam(net, 1e-2, 10);
        Mlp.Gradients g = net.newGradients();
        float[] x = new float[2];
        for (int step = 0; step < 3000; step++) {
            g.clear();
            for (int b = 0; b < 16; b++) {
                x[0] = (float) rng.randDouble(-1, 1);
                x[1] = (float) rng.randDouble(-1, 1);
                double error = net.forward(x, 0) - target(x);
                net.backward(x, 0, error, g);
            }
            adam.step(net, g, 1.0 / 16);
        }
        double mse = 0;
        for (int k = 0; k < 500; k++) {
            x[0] = (float) rng.randDouble(-1, 1);
            x[1] = (float) rng.randDouble(-1, 1);
            double error = net.forward(x, 0) - target(x);
            mse += error * error / 500;
        }
        assertTrue(mse < 2e-3, "mse " + mse);
    }

    private static double target(float[] x) {
        return x[0] * x[1] + 0.5 * Math.abs(x[0]) - 0.2;
    }
}
