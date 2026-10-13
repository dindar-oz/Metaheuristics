package problems.wflop.operators;

import base.MutationOperator;
import problems.wflop.CellLayout;
import util.random.RNG;

/**
 * Moves a random turbine to a free cell among its 8 neighbors (a small, local move; keeps the number of turbines).
 * If that turbine has no free neighbor, it moves to a random free cell instead.
 */
public final class MoveTurbineToNeighbor implements MutationOperator<CellLayout> {

    @Override
    public CellLayout apply(CellLayout layout, RNG rng) {
        if (layout.size() == 0 || layout.size() == layout.gridCells())
            return layout;
        int side = layout.side();
        int from = layout.randomOccupiedCell(rng);
        int row = from / side, column = from % side;
        int[] free = new int[8];
        int count = 0;
        for (int dr = -1; dr <= 1; dr++)
            for (int dc = -1; dc <= 1; dc++) {
                int r = row + dr, c = column + dc;
                if ((dr != 0 || dc != 0) && r >= 0 && r < side && c >= 0 && c < side && !layout.contains(r * side + c))
                    free[count++] = r * side + c;
            }
        int to = count > 0 ? free[rng.randInt(count)] : layout.randomFreeCell(rng);
        return layout.withMoved(from, to);
    }

    @Override
    public String toString() {
        return "MoveTurbineToNeighbor";
    }
}
