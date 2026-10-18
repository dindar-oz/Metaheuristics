package experimentrunner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SummaryTest {

    @Test
    void wilcoxonIsExact() {
        assertEquals(2 / 1024.0, Summary.wilcoxon(new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}), 1e-12);
        // W+ = 25 of 55, n = 10
        assertEquals(0.845703125, Summary.wilcoxon(new double[]{1, -2, 3, -4, 5, -6, 7, -8, 9, -10}), 1e-9);
        // W- = 1, n = 5: P(W- <= 1) = 2/32, two-sided 4/32
        assertEquals(0.125, Summary.wilcoxon(new double[]{-1, 2, 3, 4, 5}), 1e-12);
        // zeros are dropped, ties get average ranks
        assertEquals(Summary.wilcoxon(new double[]{1, 2, 3}), Summary.wilcoxon(new double[]{0, 1, 2, 3, 0}), 1e-12);
        assertEquals(1, Summary.wilcoxon(new double[]{0, 0}));
        assertEquals(0.25, Summary.wilcoxon(new double[]{2, 2, 2}), 1e-12);
    }

    @Test
    void holmAdjusts() {
        assertArrayEquals(new double[]{0.03, 0.06, 0.06}, Summary.holm(new double[]{0.01, 0.04, 0.03}), 1e-12);
        assertArrayEquals(new double[]{0.7}, Summary.holm(new double[]{0.7}), 1e-12);
        assertArrayEquals(new double[]{1.0, 1.0}, Summary.holm(new double[]{0.6, 0.9}), 1e-12);
    }

    @Test
    void tQuantile() {
        assertEquals(2.145, Summary.tQuantile975(14), 1e-3);
        assertEquals(12.706, Summary.tQuantile975(1), 1e-3);
    }
}
