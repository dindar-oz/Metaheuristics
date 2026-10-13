package problems.wflop.operators;

import metaheuristic.ea.base.CrossOverOperator;
import problems.wflop.CellLayout;
import util.random.RNG;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The child keeps every cell both parents occupy, then takes random cells occupied by only one parent until it has
 * its turbine count: a random count between the parents' counts, limited to [minTurbines, maxTurbines]
 * (with a fixed count, exactly that count).
 */
public final class LayoutCrossover implements CrossOverOperator<CellLayout> {
    private final int minTurbines;
    private final int maxTurbines;

    public LayoutCrossover(int minTurbines, int maxTurbines) {
        this.minTurbines = minTurbines;
        this.maxTurbines = maxTurbines;
    }

    @Override
    public List<CellLayout> apply(CellLayout a, CellLayout b, RNG rng) {
        if (a.side() != b.side())
            throw new IllegalArgumentException("parents are on different grids");
        int low = Math.min(a.size(), b.size()), high = Math.max(a.size(), b.size());
        int target = Math.max(minTurbines, Math.min(maxTurbines, low + rng.randInt(high - low + 1)));

        List<Integer> child = new ArrayList<>();
        List<Integer> pool = new ArrayList<>();
        for (int k = 0; k < a.size(); k++)
            (b.contains(a.cell(k)) ? child : pool).add(a.cell(k));
        for (int k = 0; k < b.size(); k++)
            if (!a.contains(b.cell(k)))
                pool.add(b.cell(k));

        // random cells from the pool, in random order (partial Fisher-Yates)
        for (int k = 0; k < pool.size() && child.size() < target; k++) {
            int j = k + rng.randInt(pool.size() - k);
            Collections.swap(pool, k, j);
            child.add(pool.get(k));
        }
        CellLayout layout = new CellLayout(a.side(), child.stream().mapToInt(Integer::intValue).toArray());
        while (layout.size() < target)   // only if the parents together have too few cells
            layout = layout.withAdded(layout.randomFreeCell(rng));
        return Collections.singletonList(layout);
    }

    @Override
    public String toString() {
        return "LayoutCrossover";
    }
}
