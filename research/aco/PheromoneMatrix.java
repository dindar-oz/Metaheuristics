package core.algorithm.aco;

import core.base.OptimizationProblem;
import core.base.Solution;

import java.util.Arrays;
import java.util.concurrent.locks.StampedLock;

/**
 * Base class for dense pheromone tables stored as a flat {@code rows x cols} array.
 *
 * <h2>Lazy evaporation</h2>
 * Evaporating means multiplying every cell by {@code (1 - ratio)}, which is O(rows*cols) and was
 * the main bottleneck of the parallel algorithm (every ant update rewrote the whole matrix under
 * a lock). Instead, the effective pheromone is kept as {@code stored[i] * scale}: evaporation only
 * shrinks {@code scale} (O(1)) and deposits are divided by {@code scale} before being stored.
 * When {@code scale} gets close to underflow, the matrix is renormalised once (O(rows*cols)).
 *
 * <h2>Concurrency</h2>
 * Every write ({@link #update}, {@link #evaporate}) takes an exclusive lock, so the
 * evaporate-then-deposit step of one solution is atomic. Reads use {@link StampedLock}'s
 * optimistic mode: normally no lock is taken, and if a writer ran during the read, the read is
 * retried under a shared lock. Ants therefore never see a half-renormalised matrix. In the
 * asynchronous algorithm they may still see values that are one update old, which is normal
 * for asynchronous ACO.
 *
 * <h2>MAX-MIN bounds</h2>
 * {@link #withBounds(double, double)} enables MAX-MIN Ant System style limits. The upper bound
 * is applied to the stored value whenever a cell is written. The lower bound is applied when a
 * cell is read, because lazy evaporation never touches individual cells.
 */
public abstract class PheromoneMatrix implements PheromoneTrails {

    /** Once {@code scale} drops below this value the stored values are renormalised. */
    private static final double RENORMALISE_BELOW = 1e-100;

    protected final double initialValue;
    protected final int colonySize;
    protected final double evaporationRatio;

    private final StampedLock lock = new StampedLock();
    private double[] values = new double[0];
    private double scale = 1.0;
    private int cols;

    private double tauMin = 0.0;
    private double tauMax = Double.POSITIVE_INFINITY;

    /**
     * @param initialValue     initial pheromone on every cell (subclasses may interpret values
     *                         {@code <= 0} as "derive automatically")
     * @param colonySize       number of ants sharing the trail; evaporation per solution is
     *                         {@code evaporationRatio / colonySize}
     * @param evaporationRatio fraction of pheromone evaporating per colony iteration, in (0, 1]
     */
    protected PheromoneMatrix(double initialValue, int colonySize, double evaporationRatio) {
        if (colonySize <= 0)
            throw new IllegalArgumentException("colonySize must be positive: " + colonySize);
        if (!(evaporationRatio > 0 && evaporationRatio <= 1))
            throw new IllegalArgumentException("evaporationRatio must be in (0, 1]: " + evaporationRatio);
        this.initialValue = initialValue;
        this.colonySize = colonySize;
        this.evaporationRatio = evaporationRatio;
    }

    /**
     * Enables MAX-MIN Ant System bounds on the effective pheromone values.
     *
     * @return {@code this} for chaining
     */
    public PheromoneMatrix withBounds(double tauMin, double tauMax) {
        if (!(tauMin >= 0 && tauMin <= tauMax))
            throw new IllegalArgumentException("Require 0 <= tauMin <= tauMax");
        this.tauMin = tauMin;
        this.tauMax = tauMax;
        return this;
    }

    // ------------------------------------------------------------------ setup

    /** Allocates a {@code rows x cols} table filled with {@code initial}; call from {@link #init}. */
    protected final void allocate(int rows, int cols, double initial) {
        long stamp = lock.writeLock();
        try {
            this.cols = cols;
            this.values = new double[rows * cols];
            Arrays.fill(values, Math.min(initial, tauMax));
            this.scale = 1.0;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Creates an unconfigured instance of the concrete class for a colony of the given size.
     * {@link #forColony(int)} copies the bounds onto it.
     */
    protected abstract PheromoneMatrix create(int colonySize);

    @Override
    public final PheromoneTrails forColony(int colonySize) {
        return create(colonySize).withBounds(tauMin, tauMax);
    }

    // ------------------------------------------------------------------ reads

    /** Flat index of cell {@code (row, col)}; pass it to {@link #get(int)} or {@link #read}. */
    public final int index(int row, int col) {
        return row * cols + col;
    }

    public final double get(int row, int col) {
        return get(index(row, col));
    }

    /** Effective pheromone of the cell at flat index {@code index}. */
    public final double get(int index) {
        long stamp = lock.tryOptimisticRead();
        double v = values[index] * scale;
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                v = values[index] * scale;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return clamp(v);
    }

    /**
     * Reads {@code count} cells in one consistent snapshot:
     * {@code out[i] = pheromone(indices[i])}. This is the preferred way for ants to fetch
     * all candidate values of one construction step.
     */
    public final void read(int[] indices, int count, double[] out) {
        long stamp = lock.tryOptimisticRead();
        readInto(indices, count, out);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                readInto(indices, count, out);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        for (int i = 0; i < count; i++) {
            out[i] = clamp(out[i]);
        }
    }

    private void readInto(int[] indices, int count, double[] out) {
        double[] v = values;
        double s = scale;
        for (int i = 0; i < count; i++) {
            out[i] = v[indices[i]] * s;
        }
    }

    private double clamp(double v) {
        return v < tauMin ? tauMin : Math.min(v, tauMax);
    }

    // ------------------------------------------------------------------ writes

    @Override
    public final void update(OptimizationProblem problem, Solution solution) {
        long stamp = lock.writeLock();
        try {
            evaporateLocked(evaporationRatio / colonySize);
            deposit(problem, solution);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /** Evaporates every cell by {@code ratio} in O(1) (amortised). */
    public final void evaporate(double ratio) {
        long stamp = lock.writeLock();
        try {
            evaporateLocked(ratio);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    private void evaporateLocked(double ratio) {
        scale *= (1 - ratio);
        if (scale < RENORMALISE_BELOW) {
            for (int i = 0; i < values.length; i++) {
                values[i] *= scale;
            }
            scale = 1.0;
        }
    }

    /**
     * Adds pheromone for {@code solution}. Called with the write lock held, right after this
     * solution's evaporation step. Use {@link #add} and {@link #blend} to change cells.
     */
    protected abstract void deposit(OptimizationProblem problem, Solution solution);

    /** Adds {@code delta} to the effective pheromone of a cell. Only call from {@link #deposit}. */
    protected final void add(int index, double delta) {
        values[index] += delta / scale;
        capAtMax(index);
    }

    /**
     * Moves a cell towards {@code target} by the given learning rate:
     * {@code tau += rate * (target - tau)}. Only call from {@link #deposit}.
     */
    protected final void blend(int index, double target, double rate) {
        double effective = values[index] * scale;
        values[index] = (effective + rate * (target - effective)) / scale;
        capAtMax(index);
    }

    private void capAtMax(int index) {
        if (values[index] * scale > tauMax)
            values[index] = tauMax / scale;
    }

    @Override
    public double getEvaporationRatio() {
        return evaporationRatio;
    }
}
