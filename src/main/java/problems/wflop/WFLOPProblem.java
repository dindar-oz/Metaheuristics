package problems.wflop;

import base.ContinuousProblem;
import representation.DoubleVector;

/**
 * Wind farm layout optimization as a continuous problem, as in Yu &amp; Zhang (2024), Section 3.3: a real vector is
 * decoded into a set of occupied grid cells and the layout is minimized by cost / power (Eq. 7).
 * Subclasses define the encoding. Immutable and thread-safe.
 */
public abstract class WFLOPProblem implements ContinuousProblem {

    // cost of vectors that decode to an empty layout (Fig. 9: "non-feasible solution")
    public static final double INFEASIBLE_COST = Double.MAX_VALUE;

    protected final WindFarm farm;

    protected WFLOPProblem(WindFarm farm) {
        this.farm = farm;
    }

    // The occupied cells encoded by the vector (distinct, may be empty)
    public abstract int[] decode(DoubleVector solution);

    public LayoutEvaluation evaluate(DoubleVector solution) {
        return farm.evaluate(decode(solution));
    }

    @Override
    public double cost(DoubleVector solution) {
        int[] cells = decode(solution);
        if (cells.length == 0)
            return INFEASIBLE_COST;
        return WindFarm.cost(cells.length) / farm.power(cells);
    }

    @Override
    public boolean isFeasible(DoubleVector solution) {
        return decode(solution).length > 0;
    }

    public WindFarm farm() {
        return farm;
    }
}
