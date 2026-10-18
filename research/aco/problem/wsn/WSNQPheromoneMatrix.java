package core.algorithm.aco.problem.wsn;

import core.algorithm.aco.PheromoneMatrix;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.problems.wsn.WSN;
import core.representation.BitString;

import java.util.BitSet;

/**
 * Relative-improvement pheromone for the WSN problem. The reference cost {@code base} is the
 * cost of switching every sensor on. A solution with cost {@code c} deposits
 * {@code learningRate * max(0, (base - c) / base)} on each sensor it uses, so only solutions
 * better than "everything on" reinforce the trail.
 * <p>
 * Evaporation happens on every update. Previously it only happened for solutions better than
 * {@code base}, which made the decay rate depend on solution quality.
 */
public class WSNQPheromoneMatrix extends PheromoneMatrix {

    private static final double DEFAULT_LEARNING_RATE = 0.9;

    private final double learningRate;
    private double base;

    public WSNQPheromoneMatrix(double initialValue, int colonySize, double evaporationRatio) {
        this(initialValue, colonySize, evaporationRatio, DEFAULT_LEARNING_RATE);
    }

    public WSNQPheromoneMatrix(double initialValue, int colonySize, double evaporationRatio, double learningRate) {
        super(initialValue, colonySize, evaporationRatio);
        this.learningRate = learningRate;
    }

    @Override
    public void init(OptimizationProblem problem) {
        WSN wsn = (WSN) problem.model();
        base = allSensorsOnCost(problem, wsn.getSolutionSize());
        allocate(1, wsn.getSolutionSize(), initialValue);
    }

    private static double allSensorsOnCost(OptimizationProblem problem, int size) {
        BitSet all = new BitSet(size);
        all.set(0, size);
        return problem.objectiveValue(new BitString(all, size));
    }

    @Override
    protected void deposit(OptimizationProblem problem, Solution s) {
        double improvement = Math.max(0, (base - s.objectiveValue()) / base);
        if (improvement == 0)
            return;

        BitSet bits = ((BitString) s.getRepresentation()).getBitSet();
        double delta = learningRate * improvement;
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            add(i, delta);
        }
    }

    @Override
    protected PheromoneMatrix create(int colonySize) {
        return new WSNQPheromoneMatrix(initialValue, colonySize, evaporationRatio, learningRate);
    }
}
