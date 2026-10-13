package problems.wflop.rl;

import metaheuristic.rl.FeaturedConstruction;
import problems.wflop.CellLayout;
import problems.wflop.DiscreteWFLOP;
import problems.wflop.JensenWakeModel;
import problems.wflop.WakeMatrix;
import problems.wflop.WindFarm;

import java.util.Arrays;

import static problems.wflop.rl.LayoutFeature.*;

/**
 * One episode of ConstructiveWFLOP: turbines are placed one at a time, starting from an empty farm.
 * Actions 0 .. cells - 1 place a turbine in that free cell; action STOP = cells ends a layout of variable size
 * (allowed from minTurbines on). The episode ends on STOP or when maxTurbines are placed.
 * <p>
 * With obj(L) = cost(n) / power(L) and obj* the objective of one unwaked turbine, placing the first turbine is
 * rewarded with -obj(L1) / obj* and every later one with (obj(L_t-1) - obj(L_t)) / obj*: the rewards telescope to
 * -obj(final layout) / obj*. Powers come from the farm itself, evaluated incrementally along the episode.
 * <p>
 * The features of the actions (see LayoutFeature) are computed from the farm's WakeMatrix and quantities kept up to
 * date as turbines are placed: the sum of squared deficits the placed turbines cause at every cell and wind state,
 * and the exposures of every cell to and from the free cells.
 */
final class LayoutConstruction implements FeaturedConstruction<CellLayout> {

    private final ConstructiveWFLOP problem;
    private final DiscreteWFLOP discrete;
    private final WindFarm farm;
    private final WakeMatrix wakes;
    private final int cells;
    private final int states;
    private final int stop;
    private final double freePower;

    private CellLayout layout;
    private final boolean[] occupied;
    private final double[] squaredDeficits;   // [s * cells + j]: sum of squared deficits the turbines cause at cell j
    private final double[] upwind;            // UPWIND_EXPOSURE of every cell, before scaling
    private final double[] downwind;          // DOWNWIND_EXPOSURE of every cell, before scaling
    private double power;
    private double objective;
    private boolean stopped;
    private double[][] features;              // of the actions in the current state; null until computed

    LayoutConstruction(ConstructiveWFLOP problem) {
        this.problem = problem;
        this.discrete = problem.problem();
        this.farm = discrete.farm();
        this.wakes = farm.wakeMatrix();
        this.cells = farm.cellCount();
        this.states = farm.stateCount();
        this.stop = cells;
        this.freePower = farm.freeTurbinePower(-1);
        this.layout = new CellLayout(farm.cellsPerSide(), new int[0]);
        this.occupied = new boolean[cells];
        this.squaredDeficits = new double[states * cells];
        this.upwind = problem.initialUpwindExposure();
        this.downwind = problem.initialDownwindExposure();
    }

    private int turbines() {
        return layout.size();
    }

    private boolean canStop() {
        return !discrete.isFixedCount() && turbines() >= discrete.minTurbines();
    }

    @Override
    public boolean isComplete() {
        return stopped || turbines() == discrete.maxTurbines();
    }

    @Override
    public int[] feasibleActions() {
        if (isComplete())
            return new int[0];
        int[] actions = new int[cells - turbines() + (canStop() ? 1 : 0)];
        int k = 0;
        for (int c = 0; c < cells; c++)
            if (!occupied[c])
                actions[k++] = c;
        if (canStop())
            actions[k] = stop;
        return actions;
    }

    @Override
    public double step(int action) {
        if (isComplete())
            throw new IllegalStateException("the layout is complete");
        features = null;
        if (action == stop) {
            if (!canStop())
                throw new IllegalArgumentException("cannot stop with " + turbines() + " turbines");
            stopped = true;
            return 0;
        }
        if (action < 0 || action >= cells || occupied[action])
            throw new IllegalArgumentException("cell " + action + " is not free");

        layout = layout.withAdded(action);
        occupied[action] = true;
        for (int e = wakes.outBegin(action); e < wakes.outEnd(action); e++) {
            int j = wakes.outTarget(e), s = wakes.outState(e);
            double v = wakes.outSquaredDeficit(e);
            squaredDeficits[s * cells + j] += v;
            upwind[j] -= problem.stateWeight(s) * v;            // the cell is no longer free
        }
        for (int s = 0; s < states; s++)
            for (int e = wakes.inBegin(action, s); e < wakes.inEnd(action, s); e++)
                downwind[wakes.inSource(e)] -= problem.stateWeight(s) * wakes.inSquaredDeficit(e);

        power = farm.power(layout);
        double next = WindFarm.cost(turbines()) / power;
        double reward = (turbines() == 1 ? -next : objective - next) / problem.referenceObjective();
        objective = next;
        return reward;
    }

    @Override
    public CellLayout solution() {
        return layout;
    }

    @Override
    public double[][] actionFeatures() {
        if (features == null)
            features = computeFeatures();
        double[][] copy = new double[features.length][];
        for (int k = 0; k < copy.length; k++)
            copy[k] = features[k].clone();
        return copy;
    }

    // the exact rewards: IMPROVEMENT / turbines after the action for placements, 0 for STOP
    @Override
    public double[] actionRewards() {
        if (features == null)
            features = computeFeatures();
        double[] rewards = new double[features.length];
        for (int k = 0; k < rewards.length; k++)
            if (features[k][STOP.ordinal()] == 0)
                rewards[k] = features[k][IMPROVEMENT.ordinal()] / (turbines() + 1);
        return rewards;
    }

    // what follows a placement changes the objective by shares of 1 / turbines, like the rewards
    @Override
    public double valueScale() {
        return 1.0 / (turbines() + 1);
    }

    private double[][] computeFeatures() {
        int[] actions = feasibleActions();
        int n = turbines();
        double efficiency = n == 0 ? 1 : power / (n * freePower);
        double marginalCost = n == 0 ? 1 : (WindFarm.cost(n + 1) - WindFarm.cost(n)) / (WindFarm.cost(n) / n);
        double[][] rows = new double[actions.length][];
        for (int k = 0; k < actions.length; k++) {
            double[] row = new double[LayoutFeature.COUNT];
            row[MARGINAL_COST.ordinal()] = marginalCost;
            row[PROGRESS.ordinal()] = n / (double) discrete.maxTurbines();
            row[DENSITY.ordinal()] = n / (double) cells;
            row[EFFICIENCY.ordinal()] = efficiency;
            if (actions[k] == stop)
                row[STOP.ordinal()] = 1;
            else
                cellFeatures(actions[k], efficiency, row);
            rows[k] = row;
        }
        return rows;
    }

    private void cellFeatures(int c, double efficiency, double[] row) {
        double own = 0;
        for (int s = 0; s < states; s++)
            own += problem.stateWeight(s) * cubed(JensenWakeModel.speedRatio(squaredDeficits[s * cells + c]));
        double caused = 0;
        for (int e = wakes.outBegin(c); e < wakes.outEnd(c); e++) {
            int j = wakes.outTarget(e);
            if (!occupied[j])
                continue;
            int s = wakes.outState(e);
            double before = squaredDeficits[s * cells + j];
            caused += problem.stateWeight(s) * (cubed(JensenWakeModel.speedRatio(before))
                    - cubed(JensenWakeModel.speedRatio(before + wakes.outSquaredDeficit(e))));
        }
        int n = turbines();
        double nextObjective = WindFarm.cost(n + 1) / (power + freePower * (own - caused));
        double reward = (n == 0 ? -nextObjective : objective - nextObjective) / problem.referenceObjective();
        row[OWN_EFFICIENCY.ordinal()] = own;
        row[CAUSED_LOSS.ordinal()] = caused;
        row[MARGINAL_POWER.ordinal()] = (own - caused) / efficiency;
        row[IMPROVEMENT.ordinal()] = reward * (n + 1);
        row[UPWIND_EXPOSURE.ordinal()] = Math.max(0, upwind[c]) * ConstructiveWFLOP.EXPOSURE_SCALE;
        row[DOWNWIND_EXPOSURE.ordinal()] = Math.max(0, downwind[c]) * ConstructiveWFLOP.EXPOSURE_SCALE;
        row[EDGE_DISTANCE.ordinal()] = problem.edgeDistance(c);
    }

    private static double cubed(double v) {
        return v * v * v;
    }

    @Override
    public String toString() {
        return "LayoutConstruction{" + Arrays.toString(layout.cells()) + (stopped ? ", stopped" : "") + "}";
    }
}
