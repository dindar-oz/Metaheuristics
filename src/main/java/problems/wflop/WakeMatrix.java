package problems.wflop;

/**
 * The cell-to-cell wake matrix of a farm: the squared velocity deficit a turbine in cell i causes at a turbine in
 * cell j under wind state s. A wake only reaches the cells in a narrow downstream cone, so few of the
 * (i, j, s) triples are nonzero (about 5% on the paper's farm) and only those are stored, twice, as compressed rows:
 * <ul>
 *   <li>incoming, per (j, s): the upstream cells that wake j and their squared deficits, to evaluate a turbine;</li>
 *   <li>outgoing, per i: the (j, s) pairs that i wakes and their squared deficits, to find the turbines a change of
 *       cell i affects.</li>
 * </ul>
 * The rows are built directly, without a dense intermediate, so memory grows with the number of wakes rather than
 * with cells^2 * states. Entries are read through their index: e.g. the wakes cell i casts are the entries
 * e = outBegin(i) .. outEnd(i) - 1, each reaching outTarget(e) under wind state outState(e). Immutable.
 */
public final class WakeMatrix {

    interface SquaredDeficit {
        double at(int upstream, int downstream, int state);
    }

    private final int cells;
    private final int states;
    // incoming entries of (j, s): inStart[j * states + s] .. inStart[j * states + s + 1] - 1, by ascending source
    private final int[] inStart;
    private final int[] inSource;
    private final double[] inValue;
    // outgoing entries of cell i: outStart[i] .. outStart[i + 1] - 1, by ascending (target, state)
    private final int[] outStart;
    private final int[] outTarget;
    private final int[] outState;
    private final double[] outValue;

    WakeMatrix(int cells, int states, SquaredDeficit deficit) {
        this.cells = cells;
        this.states = states;

        // incoming rows, in (j, s, i) order; arrays grow as needed
        inStart = new int[cells * states + 1];
        int[] source = new int[Math.max(16, cells * states)];
        double[] value = new double[source.length];
        int e = 0;
        for (int j = 0; j < cells; j++)
            for (int s = 0; s < states; s++) {
                inStart[j * states + s] = e;
                for (int i = 0; i < cells; i++) {
                    if (i == j)
                        continue;
                    double v = deficit.at(i, j, s);
                    if (v == 0)
                        continue;
                    if (e == source.length) {
                        source = java.util.Arrays.copyOf(source, 2 * e);
                        value = java.util.Arrays.copyOf(value, 2 * e);
                    }
                    source[e] = i;
                    value[e++] = v;
                }
            }
        inStart[cells * states] = e;
        inSource = java.util.Arrays.copyOf(source, e);
        inValue = java.util.Arrays.copyOf(value, e);

        // outgoing rows: the transpose, filled in ascending (j, s) so that each row is ordered by (target, state)
        outStart = new int[cells + 1];
        for (int k = 0; k < e; k++)
            outStart[inSource[k] + 1]++;
        for (int i = 0; i < cells; i++)
            outStart[i + 1] += outStart[i];
        outTarget = new int[e];
        outState = new int[e];
        outValue = new double[e];
        int[] next = java.util.Arrays.copyOf(outStart, cells);
        for (int js = 0; js < cells * states; js++)
            for (int k = inStart[js]; k < inStart[js + 1]; k++) {
                int slot = next[inSource[k]]++;
                outTarget[slot] = js / states;
                outState[slot] = js % states;
                outValue[slot] = inValue[k];
            }
    }

    public int cells() {
        return cells;
    }

    public int states() {
        return states;
    }

    // ---- incoming: the wakes that reach cell j under state s

    public int inBegin(int j, int s) {
        return inStart[j * states + s];
    }

    public int inEnd(int j, int s) {
        return inStart[j * states + s + 1];
    }

    // the upstream cell of incoming entry e
    public int inSource(int e) {
        return inSource[e];
    }

    public double inSquaredDeficit(int e) {
        return inValue[e];
    }

    // ---- outgoing: the wakes cell i casts

    public int outBegin(int i) {
        return outStart[i];
    }

    public int outEnd(int i) {
        return outStart[i + 1];
    }

    // the downstream cell of outgoing entry e
    public int outTarget(int e) {
        return outTarget[e];
    }

    public int outState(int e) {
        return outState[e];
    }

    public double outSquaredDeficit(int e) {
        return outValue[e];
    }

    /**
     * Sum of the squared deficits the occupied cells cause at cell j in state s (Eq. 4 before the root).
     * {@code occupied[i]} is 1 for an occupied cell and 0 otherwise: multiplying instead of branching avoids
     * unpredictable branches and adds exact zeros, so the sum is the same as over the occupied cells alone.
     */
    public double incoming(int j, int s, double[] occupied) {
        int js = j * states + s;
        double sum = 0;
        for (int e = inStart[js]; e < inStart[js + 1]; e++)
            sum += inValue[e] * occupied[inSource[e]];
        return sum;
    }

    // fraction of the (upstream, downstream, state) triples with a nonzero deficit
    public double density() {
        return inSource.length / ((double) cells * (cells - 1) * states);
    }
}
