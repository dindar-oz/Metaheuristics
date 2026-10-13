package util;

/**
 * Places n points in the plane so that their Euclidean distances are as close as possible to given distances.
 * <ol>
 *   <li>Classical (Torgerson) MDS: the two leading eigenvectors of B = -1/2 J D^2 J, found by subspace iteration
 *       with on-the-fly distance access (no n x n matrix is stored). If the distances come from points in the plane
 *       this recovers them exactly, up to rotation, reflection and translation.</li>
 *   <li>If the distances are not exactly planar, SMACOF stress majorization refines the placement.</li>
 * </ol>
 * The result reports how faithful the placement is (normalized stress and maximum relative distortion).
 */
public final class MultidimensionalScaling {

    // symmetric, non-negative distance between items i and j
    @FunctionalInterface
    public interface Distances {
        double distance(int i, int j);
    }

    private static final int MAX_EIGEN_ITERATIONS = 1000;
    private static final int MAX_SMACOF_ITERATIONS = 300;
    private static final int SMACOF_SIZE_LIMIT = 2000; // O(n^2) per iteration
    private static final double EXACT_STRESS = 1e-9;

    private MultidimensionalScaling() {
    }

    // Asymmetric input is symmetrized as (d(i,j) + d(j,i)) / 2
    public static Embedding embed(int n, Distances input) {
        if (n < 1)
            throw new IllegalArgumentException("need at least one point");
        Distances d = (i, j) -> i == j ? 0 : 0.5 * (input.distance(i, j) + input.distance(j, i));
        double[][] points = new double[n][2];
        if (n == 2)
            points[1][0] = d.distance(0, 1);
        if (n > 2) {
            points = classical(n, d);
            double stress = stress(n, d, points);
            if (stress > EXACT_STRESS && n <= SMACOF_SIZE_LIMIT)
                points = smacof(n, d, points, stress);
        }
        return new Embedding(points, stress(n, d, points), maxDistortion(n, d, points));
    }

    // Classical MDS only, without refinement (for comparison)
    static Embedding classicalEmbedding(int n, Distances d) {
        double[][] points = classical(n, d);
        return new Embedding(points, stress(n, d, points), maxDistortion(n, d, points));
    }

    // ------------------------------------------------------------------ classical MDS

    private static double[][] classical(int n, Distances d) {
        // subspace iteration for the two largest eigenvalues of B, starting from a fixed pseudo-random basis
        double[][] v = new double[2][n];
        long state = 12345;
        for (int k = 0; k < 2; k++)
            for (int i = 0; i < n; i++) {
                state = state * 6364136223846793005L + 1442695040888963407L;
                v[k][i] = (state >>> 11) / (double) (1L << 53) - 0.5;
            }
        orthonormalize(v);
        double[] previous = {Double.NaN, Double.NaN};
        double[] lambda = new double[2];
        for (int it = 0; it < MAX_EIGEN_ITERATIONS; it++) {
            double[][] w = {multiplyB(n, d, v[0]), multiplyB(n, d, v[1])};
            for (int k = 0; k < 2; k++)
                lambda[k] = dot(v[k], w[k]);
            v = w;
            orthonormalize(v);
            if (Math.abs(lambda[0] - previous[0]) <= 1e-12 * Math.abs(lambda[0])
                    && Math.abs(lambda[1] - previous[1]) <= 1e-12 * Math.abs(lambda[0]))
                break;
            previous = lambda.clone();
        }
        // Rayleigh-Ritz on the 2-dimensional subspace
        double[] b0 = multiplyB(n, d, v[0]), b1 = multiplyB(n, d, v[1]);
        double h00 = dot(v[0], b0), h01 = dot(v[0], b1), h11 = dot(v[1], b1);
        double mean = (h00 + h11) / 2, diff = (h00 - h11) / 2, radius = Math.hypot(diff, h01);
        double l1 = mean + radius, l2 = mean - radius;
        double angle = 0.5 * Math.atan2(2 * h01, h00 - h11);
        double c = Math.cos(angle), s = Math.sin(angle);
        double[][] points = new double[n][2];
        double s1 = Math.sqrt(Math.max(0, l1)), s2 = Math.sqrt(Math.max(0, l2));
        for (int i = 0; i < n; i++) {
            points[i][0] = s1 * (c * v[0][i] + s * v[1][i]);
            points[i][1] = s2 * (-s * v[0][i] + c * v[1][i]);
        }
        return points;
    }

    // B x = -1/2 J A J x with A_ij = d_ij^2 and J = I - 11'/n
    private static double[] multiplyB(int n, Distances d, double[] x) {
        double mean = 0;
        for (double v : x)
            mean += v;
        mean /= n;
        double[] u = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j = 0; j < n; j++) {
                double dij = d.distance(i, j);
                sum += dij * dij * (x[j] - mean);
            }
            u[i] = sum;
        }
        double uMean = 0;
        for (double v : u)
            uMean += v;
        uMean /= n;
        for (int i = 0; i < n; i++)
            u[i] = -0.5 * (u[i] - uMean);
        return u;
    }

    private static void orthonormalize(double[][] v) {
        normalize(v[0]);
        double p = dot(v[0], v[1]);
        for (int i = 0; i < v[1].length; i++)
            v[1][i] -= p * v[0][i];
        normalize(v[1]);
    }

    private static void normalize(double[] x) {
        double norm = Math.sqrt(dot(x, x));
        if (norm == 0)
            return;
        for (int i = 0; i < x.length; i++)
            x[i] /= norm;
    }

    private static double dot(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++)
            s += a[i] * b[i];
        return s;
    }

    // ------------------------------------------------------------------ SMACOF refinement

    private static double[][] smacof(int n, Distances d, double[][] start, double startStress) {
        double[][] x = start;
        double stress = startStress;
        for (int it = 0; it < MAX_SMACOF_ITERATIONS; it++) {
            double[][] next = new double[n][2];
            for (int i = 0; i < n; i++) {
                double sx = 0, sy = 0;
                for (int j = 0; j < n; j++) {
                    if (i == j)
                        continue;
                    double dx = x[i][0] - x[j][0], dy = x[i][1] - x[j][1];
                    double current = Math.hypot(dx, dy);
                    if (current > 0) {
                        double ratio = d.distance(i, j) / current;
                        sx += ratio * dx;
                        sy += ratio * dy;
                    }
                }
                next[i][0] = sx / n;
                next[i][1] = sy / n;
            }
            double nextStress = stress(n, d, next);
            if (nextStress > stress)
                break;
            boolean converged = stress - nextStress <= 1e-7 * stress;
            x = next;
            stress = nextStress;
            if (converged || stress <= EXACT_STRESS)
                break;
        }
        return x;
    }

    // ------------------------------------------------------------------ quality

    // sqrt( sum (placed - target)^2 / sum target^2 ) over all pairs; 0 = exact
    public static double stress(int n, Distances d, double[][] points) {
        double num = 0, den = 0;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                double target = d.distance(i, j);
                double placed = Math.hypot(points[i][0] - points[j][0], points[i][1] - points[j][1]);
                num += (placed - target) * (placed - target);
                den += target * target;
            }
        return den == 0 ? 0 : Math.sqrt(num / den);
    }

    // max over pairs with positive target distance of |placed / target - 1|
    public static double maxDistortion(int n, Distances d, double[][] points) {
        double worst = 0;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                double target = d.distance(i, j);
                if (target > 0) {
                    double placed = Math.hypot(points[i][0] - points[j][0], points[i][1] - points[j][1]);
                    worst = Math.max(worst, Math.abs(placed / target - 1));
                }
            }
        return worst;
    }

    /**
     * Planar positions (in the units of the distances) and how faithful they are.
     */
    public static final class Embedding {
        private final double[][] points;
        private final double stress;
        private final double maxDistortion;

        Embedding(double[][] points, double stress, double maxDistortion) {
            this.points = points;
            this.stress = stress;
            this.maxDistortion = maxDistortion;
        }

        public double x(int i) {
            return points[i][0];
        }

        public double y(int i) {
            return points[i][1];
        }

        public int size() {
            return points.length;
        }

        // normalized stress; 0 means every placed distance equals its target distance
        public double stress() {
            return stress;
        }

        // the largest relative error of a placed distance
        public double maxDistortion() {
            return maxDistortion;
        }
    }
}
