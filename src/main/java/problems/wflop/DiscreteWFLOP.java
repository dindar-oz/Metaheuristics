package problems.wflop;

import base.InitialSolutionGenerator;
import base.MutationOperator;
import base.OptimizationProblem;
import metaheuristic.ea.base.CrossOverOperator;
import problems.wflop.operators.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wind farm layout optimization over layouts directly: a solution is the set of occupied grid cells ({@link CellLayout}),
 * so turbines can only stand at cell centers. Same farm, wake model and objective (cost / power, Eq. 7) as the
 * continuous encodings of the paper, so the objective values of both are directly comparable.
 * <p>
 * The number of turbines is limited to [minTurbines, maxTurbines]: 1..cells for Cases 1-2, exactly N for Cases 3-4.
 * Neighbors produced by the moves are evaluated incrementally from the layout they were derived from
 * (see {@link WindFarm#power(CellLayout)}). Immutable and thread-safe.
 */
public final class DiscreteWFLOP implements OptimizationProblem<CellLayout> {

    private final WindFarm farm;
    private final int minTurbines;
    private final int maxTurbines;

    private DiscreteWFLOP(WindFarm farm, int minTurbines, int maxTurbines) {
        if (minTurbines < 1 || maxTurbines > farm.cellCount() || minTurbines > maxTurbines)
            throw new IllegalArgumentException("invalid turbine range [" + minTurbines + ", " + maxTurbines + "]");
        this.farm = farm;
        this.minTurbines = minTurbines;
        this.maxTurbines = maxTurbines;
    }

    // any number of turbines from 1 to the number of cells (Cases 1 and 2)
    public static DiscreteWFLOP variable(WindFarm farm) {
        return new DiscreteWFLOP(farm, 1, farm.cellCount());
    }

    // exactly the given number of turbines (Cases 3 and 4)
    public static DiscreteWFLOP fixed(WindFarm farm, int turbines) {
        return new DiscreteWFLOP(farm, turbines, turbines);
    }

    @Override
    public double cost(CellLayout layout) {
        if (!isFeasible(layout))
            return WFLOPProblem.INFEASIBLE_COST;
        return WindFarm.cost(layout.size()) / farm.power(layout);
    }

    @Override
    public boolean isFeasible(CellLayout layout) {
        return layout.side() == farm.cellsPerSide() && layout.size() >= minTurbines && layout.size() <= maxTurbines;
    }

    public LayoutEvaluation evaluate(CellLayout layout) {
        return farm.evaluate(layout.cells());
    }

    public boolean isFixedCount() {
        return minTurbines == maxTurbines;
    }

    public int minTurbines() {
        return minTurbines;
    }

    public int maxTurbines() {
        return maxTurbines;
    }

    public WindFarm farm() {
        return farm;
    }

    // Random layouts with a uniformly random turbine count in [minTurbines, maxTurbines]
    public InitialSolutionGenerator<CellLayout> randomLayouts() {
        return (problem, rng) -> {
            int n = minTurbines + rng.randInt(maxTurbines - minTurbines + 1);
            int[] cells = rng.distinctInts(farm.cellCount(), n).stream().mapToInt(Integer::intValue).toArray();
            return new CellLayout(farm.cellsPerSide(), cells);
        };
    }

    // Moves that keep the layout feasible: relocations, plus additions and removals if the count may vary
    public List<MutationOperator<CellLayout>> neighborhood() {
        List<MutationOperator<CellLayout>> moves = new ArrayList<>();
        moves.add(new MoveTurbine());
        moves.add(new MoveTurbineToNeighbor());
        if (!isFixedCount()) {
            moves.add(new AddTurbine(maxTurbines));
            moves.add(new RemoveTurbine(minTurbines));
        }
        return Collections.unmodifiableList(moves);
    }

    public CrossOverOperator<CellLayout> crossover() {
        return new LayoutCrossover(minTurbines, maxTurbines);
    }

    @Override
    public String toString() {
        return "DiscreteWFLOP(" + (isFixedCount() ? minTurbines + " turbines" : minTurbines + "-" + maxTurbines + " turbines")
                + ", " + farm + ")";
    }
}
