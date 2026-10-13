package problems.wflop.operators;

import base.MutationOperator;
import problems.wflop.CellLayout;
import util.random.RNG;

/**
 * Removes a random turbine; at the minimum number of turbines it moves a turbine instead.
 */
public final class RemoveTurbine implements MutationOperator<CellLayout> {
    private final int minTurbines;
    private final MoveTurbine fallback = new MoveTurbine();

    public RemoveTurbine(int minTurbines) {
        this.minTurbines = minTurbines;
    }

    @Override
    public CellLayout apply(CellLayout layout, RNG rng) {
        if (layout.size() <= minTurbines)
            return fallback.apply(layout, rng);
        return layout.withRemoved(layout.randomOccupiedCell(rng));
    }

    @Override
    public String toString() {
        return "RemoveTurbine";
    }
}
