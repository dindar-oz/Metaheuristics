package problems.wflop.operators;

import base.MutationOperator;
import problems.wflop.CellLayout;
import util.random.RNG;

/**
 * Moves a random turbine to a random free cell (keeps the number of turbines).
 */
public final class MoveTurbine implements MutationOperator<CellLayout> {

    @Override
    public CellLayout apply(CellLayout layout, RNG rng) {
        if (layout.size() == 0 || layout.size() == layout.gridCells())
            return layout;
        return layout.withMoved(layout.randomOccupiedCell(rng), layout.randomFreeCell(rng));
    }

    @Override
    public String toString() {
        return "MoveTurbine";
    }
}
