package problems.wflop.rl;

import metaheuristic.rl.FeaturedConstruction;
import metaheuristic.rl.FeaturedConstructiveProblem;
import problems.wflop.CellLayout;
import problems.wflop.DiscreteWFLOP;
import problems.wflop.WakeMatrix;
import problems.wflop.WindFarm;
import util.random.RNG;

/**
 * A DiscreteWFLOP as a constructive problem for reinforcement learning metaheuristics (e.g. DQN): an episode places
 * turbines one at a time on an empty farm, and the state is the whole partial layout. See LayoutConstruction for the
 * actions and the rewards, whose sum is exactly -cost(layout) / obj*, with obj* the objective of a single unwaked turbine,
 * and LayoutFeature for the features of the actions, which are computed from the farm's WakeMatrix.
 * <p>
 * The features do not depend on the size of the farm, so a network trained on one farm can build layouts on another
 * (with the same turbines and cell size). Costs and feasibility are those of the DiscreteWFLOP. Immutable and thread-safe.
 */
public final class ConstructiveWFLOP implements FeaturedConstructiveProblem<CellLayout> {

    // distances to the edge are measured up to this [m]
    static final double EDGE_RANGE = 1000;
    // exposures are sums of squared deficits; this brings those of an empty farm to about [0.35, 0.85]. Squared deficits
    // decay fast with distance, so the sums hardly grow with the farm (max 0.208 on 10 x 10 cells, 0.217 on 20 x 20)
    static final double EXPOSURE_SCALE = 40;

    private final DiscreteWFLOP problem;
    private final double referenceObjective;
    private final double[] stateWeight;
    private final double[] edgeDistance;
    private final double[] upwindExposure;     // of every cell on an empty farm
    private final double[] downwindExposure;

    public ConstructiveWFLOP(DiscreteWFLOP problem) {
        this.problem = problem;
        WindFarm farm = problem.farm();
        WakeMatrix wakes = farm.wakeMatrix();
        int cells = farm.cellCount(), states = farm.stateCount();
        referenceObjective = WindFarm.cost(1) / farm.freeTurbinePower(-1);

        stateWeight = new double[states];
        for (int s = 0; s < states; s++)
            stateWeight[s] = farm.stateWeight(s);

        double size = farm.parameters().farmSize();
        edgeDistance = new double[cells];
        upwindExposure = new double[cells];
        downwindExposure = new double[cells];
        for (int c = 0; c < cells; c++) {
            double x = farm.centerX(c), y = farm.centerY(c);
            double distance = Math.min(Math.min(x, y), Math.min(size - x, size - y));
            edgeDistance[c] = Math.min(distance, EDGE_RANGE) / EDGE_RANGE;
            for (int e = wakes.outBegin(c); e < wakes.outEnd(c); e++) {
                double v = stateWeight[wakes.outState(e)] * wakes.outSquaredDeficit(e);
                downwindExposure[c] += v;
                upwindExposure[wakes.outTarget(e)] += v;
            }
        }
    }

    public DiscreteWFLOP problem() {
        return problem;
    }

    // obj*: the objective of a single turbine without wake losses, the unit of the rewards
    public double referenceObjective() {
        return referenceObjective;
    }

    double stateWeight(int s) {
        return stateWeight[s];
    }

    double edgeDistance(int cell) {
        return edgeDistance[cell];
    }

    // exposures of the cells on an empty farm, as new arrays
    double[] initialUpwindExposure() {
        return upwindExposure.clone();
    }

    double[] initialDownwindExposure() {
        return downwindExposure.clone();
    }

    @Override
    public double cost(CellLayout layout) {
        return problem.cost(layout);
    }

    @Override
    public boolean isFeasible(CellLayout layout) {
        return problem.isFeasible(layout);
    }

    @Override
    public int featureCount() {
        return LayoutFeature.COUNT;
    }

    @Override
    public FeaturedConstruction<CellLayout> start(RNG rng) {
        return new LayoutConstruction(this);
    }

    @Override
    public String toString() {
        return "Constructive" + problem;
    }
}
