package problems.wflop.operators;

import base.MutationOperator;
import problems.wflop.CellLayout;
import util.random.RNG;

/**
 * Adds a turbine in a random free cell; at the maximum number of turbines it moves a turbine instead.
 */
public final class AddTurbine implements MutationOperator<CellLayout> {
    private final int maxTurbines;
    private final MoveTurbine fallback = new MoveTurbine();

    public AddTurbine(int maxTurbines) {
        this.maxTurbines = maxTurbines;
    }

    @Override
    public CellLayout apply(CellLayout layout, RNG rng) {
        if (layout.size() >= maxTurbines || layout.size() == layout.gridCells())
            return fallback.apply(layout, rng);
        return layout.withAdded(layout.randomFreeCell(rng));
    }

    @Override
    public String toString() {
        return "AddTurbine";
    }
}
