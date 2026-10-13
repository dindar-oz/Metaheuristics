package problems.wflop;

import representation.base.Representation;
import util.random.RNG;

import java.util.Arrays;

/**
 * A wind farm layout as the set of occupied cells of a side x side grid (a turbine stands at the center of each
 * occupied cell). Immutable: the "with" methods return new layouts.
 * <p>
 * A layout also keeps its evaluation by a {@link WindFarm}, and until it is evaluated, the layout it was derived from,
 * so that neighbors are evaluated incrementally. This cache is not part of the layout's value: it never changes
 * {@code equals}, {@code hashCode} or any evaluation result.
 */
public final class CellLayout implements Representation<CellLayout> {

    private final int side;
    private final int[] cells; // sorted, distinct
    private volatile CellLayout parent; // the layout this one was derived from, until this one is evaluated
    private volatile LayoutPower power;

    public CellLayout(int side, int[] cells) {
        if (side < 1)
            throw new IllegalArgumentException("side must be positive");
        int[] sorted = cells.clone();
        Arrays.sort(sorted);
        for (int k = 0; k < sorted.length; k++) {
            if (sorted[k] < 0 || sorted[k] >= side * side)
                throw new IllegalArgumentException("cell " + sorted[k] + " is outside the grid");
            if (k > 0 && sorted[k] == sorted[k - 1])
                throw new IllegalArgumentException("cell " + sorted[k] + " is occupied twice");
        }
        this.side = side;
        this.cells = sorted;
    }

    private CellLayout(int[] sortedCells, int side, CellLayout parent) {
        this.side = side;
        this.cells = sortedCells;
        this.parent = parent;
    }

    public int side() {
        return side;
    }

    public int gridCells() {
        return side * side;
    }

    public int size() {
        return cells.length;
    }

    // the occupied cells, ascending
    public int[] cells() {
        return cells.clone();
    }

    // the k-th occupied cell in ascending order
    public int cell(int k) {
        return cells[k];
    }

    public boolean contains(int cell) {
        return Arrays.binarySearch(cells, cell) >= 0;
    }

    public CellLayout withAdded(int cell) {
        checkFree(cell);
        int[] next = Arrays.copyOf(cells, cells.length + 1);
        next[cells.length] = cell;
        Arrays.sort(next);
        return new CellLayout(next, side, this);
    }

    public CellLayout withRemoved(int cell) {
        int index = Arrays.binarySearch(cells, cell);
        if (index < 0)
            throw new IllegalArgumentException("cell " + cell + " is not occupied");
        int[] next = new int[cells.length - 1];
        System.arraycopy(cells, 0, next, 0, index);
        System.arraycopy(cells, index + 1, next, index, cells.length - index - 1);
        return new CellLayout(next, side, this);
    }

    public CellLayout withMoved(int from, int to) {
        int index = Arrays.binarySearch(cells, from);
        if (index < 0)
            throw new IllegalArgumentException("cell " + from + " is not occupied");
        checkFree(to);
        int[] next = cells.clone();
        next[index] = to;
        Arrays.sort(next);
        return new CellLayout(next, side, this);
    }

    private void checkFree(int cell) {
        if (cell < 0 || cell >= gridCells())
            throw new IllegalArgumentException("cell " + cell + " is outside the grid");
        if (contains(cell))
            throw new IllegalArgumentException("cell " + cell + " is already occupied");
    }

    // a uniformly random free cell; the grid must not be full
    public int randomFreeCell(RNG rng) {
        if (cells.length >= gridCells())
            throw new IllegalStateException("the grid is full");
        while (true) {
            int cell = rng.randInt(gridCells());
            if (!contains(cell))
                return cell;
        }
    }

    // a uniformly random occupied cell; the layout must not be empty
    public int randomOccupiedCell(RNG rng) {
        return cells[rng.randInt(cells.length)];
    }

    // ---- evaluation cache, for WindFarm

    int[] sortedCells() {
        return cells;
    }

    CellLayout parent() {
        return parent;
    }

    LayoutPower cachedPower() {
        return power;
    }

    // keeps the evaluation and releases the parent, so that chains of derived layouts are not retained
    void cache(LayoutPower power) {
        this.power = power;
        this.parent = null;
    }

    @Override
    public CellLayout copy() {
        return this; // immutable
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CellLayout))
            return false;
        CellLayout other = (CellLayout) o;
        return side == other.side && Arrays.equals(cells, other.cells);
    }

    @Override
    public int hashCode() {
        return 31 * side + Arrays.hashCode(cells);
    }

    @Override
    public String toString() {
        return "CellLayout{" + cells.length + " turbines: " + Arrays.toString(cells) + "}";
    }
}
