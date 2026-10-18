package core.algorithm.aco.problem.wsn;

import core.algorithm.aco.PheromoneMatrix;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.problems.wsn.WSN;
import core.representation.BitString;

/**
 * Ant System pheromone for the WSN problem: one value per potential sensor position (a single
 * row). Every solution deposits {@code 1 / cost} on each sensor it switches on.
 */
public class WSNPheromoneMatrix extends PheromoneMatrix {

    public WSNPheromoneMatrix(double initialValue, int colonySize, double evaporationRatio) {
        super(initialValue, colonySize, evaporationRatio);
    }

    @Override
    public void init(OptimizationProblem problem) {
        WSN wsn = (WSN) problem.model();
        allocate(1, wsn.getSolutionSize(), initialValue);
    }

    @Override
    protected void deposit(OptimizationProblem problem, Solution s) {
        BitString bs = (BitString) s.getRepresentation();
        double delta = 1.0 / s.objectiveValue();
        for (int i = bs.getBitSet().nextSetBit(0); i >= 0; i = bs.getBitSet().nextSetBit(i + 1)) {
            add(i, delta);
        }
    }

    @Override
    protected PheromoneMatrix create(int colonySize) {
        return new WSNPheromoneMatrix(initialValue, colonySize, evaporationRatio);
    }
}
