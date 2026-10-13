package problems.wflop;

import representation.DoubleVector;

/**
 * Cases 1 and 2 of Yu &amp; Zhang (2024): the number of turbines is free (1..100). The vector has one variable in
 * [0, 1] per grid cell; after rounding, 1 means the cell holds a turbine (Fig. 9). A vector without any turbine is
 * infeasible.
 */
public final class VariableTurbineWFLOP extends WFLOPProblem {

    public VariableTurbineWFLOP(WindFarm farm) {
        super(farm);
    }

    @Override
    public int[] decode(DoubleVector solution) {
        double[] x = solution.getValues();
        if (x.length != farm.cellCount())
            throw new IllegalArgumentException("expected " + farm.cellCount() + " variables, got " + x.length);
        int count = 0;
        for (double v : x)
            if (Math.round(v) >= 1)
                count++;
        int[] cells = new int[count];
        int k = 0;
        for (int cell = 0; cell < x.length; cell++)
            if (Math.round(x[cell]) >= 1)
                cells[k++] = cell;
        return cells;
    }

    @Override
    public int getDimensionCount() {
        return farm.cellCount();
    }

    @Override
    public double getLowerBound(int d) {
        return 0;
    }

    @Override
    public double getUpperBound(int d) {
        return 1;
    }

    @Override
    public String toString() {
        return "WFLOP(variable turbines, " + farm + ")";
    }
}
