package metaheuristic.rl.nn;

/**
 * The Adam optimizer (Kingma &amp; Ba 2015) for the parameters of one Mlp, with gradient norm clipping.
 * Keeps the moment estimates of that network, so it must not be used for another one.
 */
public final class Adam {

    private static final double BETA1 = 0.9;
    private static final double BETA2 = 0.999;
    private static final double EPSILON = 1e-8;

    private final double learningRate;
    private final double maxNorm;
    private final double[][] mWeights, vWeights, mBiases, vBiases;
    private int t;

    public Adam(Mlp net, double learningRate, double maxNorm) {
        if (!(learningRate > 0) || !(maxNorm > 0))
            throw new IllegalArgumentException("learningRate and maxNorm must be positive");
        this.learningRate = learningRate;
        this.maxNorm = maxNorm;
        mWeights = zerosLike(net.weights);
        vWeights = zerosLike(net.weights);
        mBiases = zerosLike(net.biases);
        vBiases = zerosLike(net.biases);
    }

    private static double[][] zerosLike(double[][] a) {
        double[][] z = new double[a.length][];
        for (int l = 0; l < a.length; l++)
            z[l] = new double[a[l].length];
        return z;
    }

    /**
     * One descent step on net along scale * g (e.g. scale = 1 / batch size for the mean gradient of a batch);
     * the scaled gradient is first clipped to norm maxNorm.
     */
    public void step(Mlp net, Mlp.Gradients g, double scale) {
        double norm = g.norm() * Math.abs(scale);
        if (norm > maxNorm)
            scale *= maxNorm / norm;
        t++;
        double c1 = 1 - Math.pow(BETA1, t), c2 = 1 - Math.pow(BETA2, t);
        for (int l = 0; l < net.weights.length; l++) {
            update(net.weights[l], g.weights[l], mWeights[l], vWeights[l], scale, c1, c2);
            update(net.biases[l], g.biases[l], mBiases[l], vBiases[l], scale, c1, c2);
        }
    }

    private void update(double[] p, double[] g, double[] m, double[] v, double scale, double c1, double c2) {
        for (int k = 0; k < p.length; k++) {
            double gk = g[k] * scale;
            m[k] = BETA1 * m[k] + (1 - BETA1) * gk;
            v[k] = BETA2 * v[k] + (1 - BETA2) * gk * gk;
            p[k] -= learningRate * (m[k] / c1) / (Math.sqrt(v[k] / c2) + EPSILON);
        }
    }
}
