package problems.wflop;

import representation.DoubleVector;

import java.util.ArrayList;
import java.util.List;

/**
 * Cases 3 and 4 of Yu &amp; Zhang (2024): a fixed number of turbines (39 in the paper). The vector has one variable
 * in [1, cells] per turbine, decoded as in Fig. 10: each value is rounded and used as a 1-based index into the list
 * of still free cells, G = {1, ..., cells}; the chosen cell is removed from G. An index beyond the end of G takes the
 * last free cell. The decoding always yields distinct cells, so every vector is feasible.
 */
public final class FixedTurbineWFLOP extends WFLOPProblem {

    private final int turbines;

    public FixedTurbineWFLOP(WindFarm farm, int turbines) {
        super(farm);
        if (turbines < 1 || turbines > farm.cellCount())
            throw new IllegalArgumentException("turbines must be between 1 and " + farm.cellCount());
        this.turbines = turbines;
    }

    public int turbines() {
        return turbines;
    }

    @Override
    public int[] decode(DoubleVector solution) {
        double[] x = solution.getValues();
        if (x.length != turbines)
            throw new IllegalArgumentException("expected " + turbines + " variables, got " + x.length);
        List<Integer> free = new ArrayList<>(farm.cellCount());
        for (int cell = 0; cell < farm.cellCount(); cell++)
            free.add(cell);
        int[] cells = new int[turbines];
        for (int i = 0; i < turbines; i++) {
            long index = Math.round(x[i]);                   // 1-based
            int position = index >= 1 && index <= free.size() ? (int) index - 1 : free.size() - 1;
            cells[i] = free.remove(position);
        }
        return cells;
    }

    @Override
    public int getDimensionCount() {
        return turbines;
    }

    @Override
    public double getLowerBound(int d) {
        return 1;
    }

    @Override
    public double getUpperBound(int d) {
        return farm.cellCount();
    }

    @Override
    public String toString() {
        return "WFLOP(" + turbines + " turbines, " + farm + ")";
    }
}
