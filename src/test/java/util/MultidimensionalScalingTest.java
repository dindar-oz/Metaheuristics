package util;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MultidimensionalScalingTest {

    private static double placed(MultidimensionalScaling.Embedding e, int i, int j) {
        return Math.hypot(e.x(i) - e.x(j), e.y(i) - e.y(j));
    }

    @Test
    void recoversPlanarDistancesExactly() {
        Random random = new Random(1);
        int n = 60;
        double[][] xy = new double[n][2];
        for (double[] p : xy) {
            p[0] = random.nextDouble() * 1000;
            p[1] = random.nextDouble() * 400;
        }
        MultidimensionalScaling.Distances d = (i, j) -> Math.hypot(xy[i][0] - xy[j][0], xy[i][1] - xy[j][1]);
        MultidimensionalScaling.Embedding e = MultidimensionalScaling.embed(n, d);
        assertTrue(e.stress() < 1e-9, "stress " + e.stress());
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                assertEquals(d.distance(i, j), placed(e, i, j), 1e-6 * d.distance(i, j) + 1e-9);
    }

    @Test
    void collinearPointsStayOnALine() {
        int n = 10;
        MultidimensionalScaling.Embedding e = MultidimensionalScaling.embed(n, (i, j) -> Math.abs(i - j) * 3.0);
        assertTrue(e.stress() < 1e-9);
        assertEquals(27.0, placed(e, 0, 9), 1e-9);
    }

    @Test
    void nonPlanarDistancesAreApproximatedAndReported() {
        // four points at mutual distance 1 (a tetrahedron) cannot be placed exactly in the plane
        MultidimensionalScaling.Embedding e = MultidimensionalScaling.embed(4, (i, j) -> i == j ? 0 : 1);
        assertTrue(e.stress() > 0.01, "non-planar input must report a positive stress");
        assertTrue(e.stress() < 0.3, "but the placement should still be reasonable, stress " + e.stress());
        assertTrue(e.maxDistortion() > 0);
    }

    @Test
    void refinementImprovesOnClassicalMdsForNonPlanarDistances() {
        Random random = new Random(3);
        int n = 40;
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                m[i][j] = m[j][i] = 1 + random.nextDouble();   // random metric-ish distances, not planar
        MultidimensionalScaling.Embedding refined = MultidimensionalScaling.embed(n, (i, j) -> m[i][j]);
        MultidimensionalScaling.Embedding classical = MultidimensionalScaling.classicalEmbedding(n, (i, j) -> m[i][j]);
        assertTrue(refined.stress() < classical.stress(),
                "refined " + refined.stress() + " vs classical " + classical.stress());
    }

    @Test
    void smallInputs() {
        MultidimensionalScaling.Embedding one = MultidimensionalScaling.embed(1, (i, j) -> 0);
        assertEquals(1, one.size());
        MultidimensionalScaling.Embedding two = MultidimensionalScaling.embed(2, (i, j) -> i == j ? 0 : 5);
        assertEquals(5, placed(two, 0, 1), 1e-12);
        assertEquals(0, two.stress(), 1e-12);
    }

    @Test
    void asymmetricInputIsSymmetrized() {
        MultidimensionalScaling.Embedding e = MultidimensionalScaling.embed(2, (i, j) -> i == j ? 0 : (i < j ? 4 : 6));
        assertEquals(5, placed(e, 0, 1), 1e-12);
    }
}
