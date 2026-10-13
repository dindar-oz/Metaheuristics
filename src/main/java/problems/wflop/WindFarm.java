package problems.wflop;

import java.util.Arrays;
import java.util.List;

/**
 * A square wind farm divided into a grid of cells (10 x 10 cells of 200 m in the paper). A turbine stands at the
 * center of each occupied cell. The farm evaluates layouts (sets of occupied cells) under a wind scenario with the
 * Jensen wake model.
 * <p>
 * Cells are numbered row by row from the south-west corner: cell k has column k % n and row k / n, and its center
 * is ((column + 0.5) * cellSize, (row + 0.5) * cellSize) with x = east, y = north.
 * <p>
 * The squared deficits between all cells are computed once, as a sparse {@link WakeMatrix}: evaluating a layout of
 * N turbines visits only the (upstream, downstream, state) triples where a wake actually reaches a turbine. A
 * {@link CellLayout} derived from an evaluated one by a few changes (a move, an addition, a removal) is evaluated
 * incrementally: only the terms of turbines in the wakes of the changed cells are recomputed.
 * Immutable and thread-safe.
 */
public final class WindFarm {

    private final WindFarmParameters parameters;
    private final JensenWakeModel wakeModel;
    private final WindScenario wind;
    private final int side;
    private final int cells;
    private final double[] speed;
    private final double[] probability;
    private final WakeMatrix matrix;

    public WindFarm(WindFarmParameters parameters, WakeMode mode, WindScenario wind) {
        this.parameters = parameters;
        this.wakeModel = new JensenWakeModel(parameters, mode);
        this.wind = wind;
        this.side = parameters.cellsPerSide();
        this.cells = side * side;

        List<WindScenario.State> states = wind.states();
        speed = new double[states.size()];
        probability = new double[states.size()];
        for (int s = 0; s < states.size(); s++) {
            speed[s] = states.get(s).speed;
            probability[s] = states.get(s).probability;
            if ((double) cells * speed[s] * speed[s] * speed[s] * LayoutPower.SCALE >= Long.MAX_VALUE)
                throw new IllegalArgumentException("wind speed " + speed[s] + " m/s is too high");
        }
        matrix = new WakeMatrix(cells, states.size(), (i, j, s) -> {
            WindScenario.State state = states.get(s);
            double d = wakeModel.deficit(centerX(j) - centerX(i), centerY(j) - centerY(i), state.flowX, state.flowY);
            return d * d;
        });
    }

    public int cellCount() {
        return cells;
    }

    public int cellsPerSide() {
        return side;
    }

    public double centerX(int cell) {
        return (cell % side + 0.5) * parameters.cellSize();
    }

    public double centerY(int cell) {
        return (cell / side + 0.5) * parameters.cellSize();
    }

    // The cell containing point (x, y)
    public int cellAt(double x, double y) {
        int column = (int) Math.floor(x / parameters.cellSize());
        int row = (int) Math.floor(y / parameters.cellSize());
        if (column < 0 || column >= side || row < 0 || row >= side)
            throw new IllegalArgumentException("(" + x + ", " + y + ") is outside the farm");
        return row * side + column;
    }

    /**
     * Expected total power [kW] of turbines at the given distinct cells, Eqs. (4) and (9):
     * sum over wind states of probability * sum over turbines of 1/2 eta rho A v^3,
     * with v = v0 (1 - sqrt(sum of squared deficits of the upstream turbines)).
     */
    public double power(int[] turbines) {
        return fullPower(turbines).power;
    }

    /**
     * The same for a layout. The result is kept with the layout, and a layout derived from an evaluated one
     * ({@link CellLayout#withMoved}, {@code withAdded}, {@code withRemoved}) is evaluated incrementally, with exactly
     * the same result as a full evaluation.
     */
    public double power(CellLayout layout) {
        if (layout.side() != side)
            throw new IllegalArgumentException("the layout is for a " + layout.side() + "x" + layout.side() + " grid");
        LayoutPower cached = layout.cachedPower();
        if (cached != null && cached.farm == this)
            return cached.power;
        CellLayout parent = layout.parent();
        LayoutPower base = parent == null ? null : parent.cachedPower();
        LayoutPower result = base != null && base.farm == this
                ? derivedPower(base, layout.sortedCells())
                : fullPower(layout.sortedCells());
        layout.cache(result);
        return result.power;
    }

    public int stateCount() {
        return speed.length;
    }

    // the cell-to-cell wakes of this farm, with wind states numbered as in wind().states()
    public WakeMatrix wakeMatrix() {
        return matrix;
    }

    /**
     * The share of wind state s in the expected power of an unwaked turbine: p_s v_s^3 / E[v^3]. The shares sum to 1,
     * and a turbine whose speed is reduced to the fraction u_s of the free speed in each state s produces
     * sum of share_s * u_s^3 times the power of an unwaked one.
     */
    public double stateWeight(int s) {
        double meanCube = wind.meanCubedSpeed();
        return meanCube == 0 ? 1.0 / speed.length : probability[s] * speed[s] * speed[s] * speed[s] / meanCube;
    }

    /**
     * Power [kW] of every turbine of the layout, in the order given: for wind state {@code state}, or the expected
     * power over all states if {@code state < 0}.
     */
    public double[] turbinePowers(int[] turbines, int state) {
        if (state >= speed.length)
            throw new IllegalArgumentException("no wind state " + state);
        double[] occupied = occupancy(turbines);
        double[] powers = new double[turbines.length];
        for (int k = 0; k < turbines.length; k++) {
            double sum = 0;
            if (state >= 0)
                sum = cubedSpeed(turbines[k], state, occupied);
            else
                for (int s = 0; s < speed.length; s++)
                    sum += probability[s] * cubedSpeed(turbines[k], s, occupied);
            powers[k] = sum * parameters.powerCoefficient();
        }
        return powers;
    }

    // fraction of the (upstream cell, downstream cell, wind state) triples in which a wake reaches the rotor
    public double wakeDensity() {
        return matrix.density();
    }

    // ------------------------------------------------------------------ evaluation

    // a layout that differs from the evaluated one in more cells than this is evaluated from scratch
    private static final int MAX_INCREMENTAL_CHANGES = 4;

    private LayoutPower fullPower(int[] turbines) {
        int states = speed.length;
        double[] occupied = occupancy(turbines);
        long[] terms = new long[turbines.length * states];
        long total = 0;
        for (int k = 0; k < turbines.length; k++)
            for (int s = 0; s < states; s++)
                total += terms[k * states + s] = term(turbines[k], s, occupied);
        return new LayoutPower(this, turbines, terms, total, parameters.powerCoefficient());
    }

    /**
     * Evaluates sorted cells from the evaluation of other sorted cells: the terms of the turbines both share are
     * copied, then only the terms in the wakes of the changed cells and those of new turbines are recomputed, and the
     * total is updated by the differences. Terms are computed as in {@link #fullPower} and summed exactly, so the
     * result is identical to a full evaluation.
     */
    private LayoutPower derivedPower(LayoutPower base, int[] turbines) {
        int states = speed.length;
        int[] previous = base.cells;
        long[] terms = new long[turbines.length * states];
        long total = base.total;
        boolean[] added = new boolean[turbines.length];
        int[] changed = new int[MAX_INCREMENTAL_CHANGES];
        int changes = 0;
        for (int a = 0, b = 0; a < previous.length || b < turbines.length; ) {
            int x = a < previous.length ? previous[a] : Integer.MAX_VALUE;
            int y = b < turbines.length ? turbines[b] : Integer.MAX_VALUE;
            if (x == y) {
                System.arraycopy(base.terms, a++ * states, terms, b++ * states, states);
                continue;
            }
            if (changes == MAX_INCREMENTAL_CHANGES)
                return fullPower(turbines);
            if (x < y) {                                        // removed
                changed[changes++] = x;
                for (int s = 0; s < states; s++)
                    total -= base.terms[a * states + s];
                a++;
            } else {                                            // added
                changed[changes++] = y;
                added[b++] = true;
            }
        }

        double[] occupied = new double[cells];
        int[] position = new int[cells];
        Arrays.fill(position, -1);
        for (int k = 0; k < turbines.length; k++) {
            occupied[turbines[k]] = 1;
            position[turbines[k]] = k;
        }
        // turbines in the wakes of the changed cells
        for (int c = 0; c < changes; c++)
            for (int e = matrix.outBegin(changed[c]); e < matrix.outEnd(changed[c]); e++) {
                int j = matrix.outTarget(e);
                int k = position[j];
                if (k >= 0 && !added[k]) {
                    int index = k * states + matrix.outState(e);
                    long term = term(j, matrix.outState(e), occupied);
                    total += term - terms[index];
                    terms[index] = term;
                }
            }
        for (int k = 0; k < turbines.length; k++)
            if (added[k])
                for (int s = 0; s < states; s++)
                    total += terms[k * states + s] = term(turbines[k], s, occupied);
        return new LayoutPower(this, turbines, terms, total, parameters.powerCoefficient());
    }

    // probability * v^3 at a turbine in cell j under state s, in fixed point
    private long term(int j, int s, double[] occupied) {
        return LayoutPower.fixed(probability[s] * cubedSpeed(j, s, occupied));
    }

    // v^3 at a turbine in cell j under state s, v = v0 (1 - sqrt(sum of squared deficits)), Eq. (4)
    private double cubedSpeed(int j, int s, double[] occupied) {
        double v = speed[s] * JensenWakeModel.speedRatio(matrix.incoming(j, s, occupied));
        return v * v * v;
    }

    private double[] occupancy(int[] turbines) {
        double[] occupied = new double[cells];
        for (int cell : turbines)
            occupied[cell] = 1;
        return occupied;
    }

    // Power [kW] of one turbine without wake losses: for wind state {@code state}, or expected if {@code state < 0}
    public double freeTurbinePower(int state) {
        double cube = state >= 0 ? speed[state] * speed[state] * speed[state] : wind.meanCubedSpeed();
        return parameters.powerCoefficient() * cube;
    }

    // Eq. (8): dimensionless annual cost of n turbines (Mosetti et al. 1994)
    public static double cost(int n) {
        return n * (2.0 / 3.0 + Math.exp(-0.00174 * n * n) / 3.0);
    }

    // Evaluates a layout: power, cost, objective (Eq. 7) and efficiencies
    public LayoutEvaluation evaluate(int[] turbines) {
        int[] sorted = checkLayout(turbines);
        double power = sorted.length == 0 ? 0 : power(sorted);
        return new LayoutEvaluation(this, sorted, power, cost(sorted.length));
    }

    // Power of n turbines without any wake losses [kW]
    public double wakeFreePower(int n) {
        return n * parameters.powerCoefficient() * wind.meanCubedSpeed();
    }

    // Rated power of n turbines used by the efficiency of Eq. (10): n * 0.3 * E[v0^3] [kW]
    public double ratedPower(int n) {
        return n * parameters.ratedPowerFactor() * wind.meanCubedSpeed();
    }

    public WindFarmParameters parameters() {
        return parameters;
    }

    public JensenWakeModel wakeModel() {
        return wakeModel;
    }

    public WindScenario wind() {
        return wind;
    }

    private int[] checkLayout(int[] turbines) {
        int[] sorted = turbines.clone();
        Arrays.sort(sorted);
        for (int k = 0; k < sorted.length; k++) {
            if (sorted[k] < 0 || sorted[k] >= cells)
                throw new IllegalArgumentException("cell " + sorted[k] + " is outside the farm");
            if (k > 0 && sorted[k] == sorted[k - 1])
                throw new IllegalArgumentException("two turbines in cell " + sorted[k]);
        }
        return sorted;
    }

    @Override
    public String toString() {
        return side + "x" + side + " farm, " + wakeModel + ", " + wind;
    }
}
