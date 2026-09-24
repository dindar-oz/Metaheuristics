package metaheuristic.sa;

import base.MutationOperator;
import base.OptimizationProblem;
import base.TerminalCondition;
import metaheuristic.AbstractSMetaheuristic;
import representation.base.Individual;
import util.random.RandUtil;

import java.util.List;

/**
 * Simulated annealing. At each temperature level innerLoopLength neighbors are tried,
 * then the temperature is lowered with the cooling schedule.
 * Stops when the terminal condition is satisfied or the temperature drops below the minimum.
 */
public class SA extends AbstractSMetaheuristic {

    private final List<MutationOperator> neighboringFunctions;
    private final CoolingSchedule coolingSchedule;
    private final double initialTemperature;
    private final double minimumTemperature;
    private int innerLoopLength = 5;

    private double currentTemperature;

    public SA(List<MutationOperator> neighboringFunctions, CoolingSchedule coolingSchedule,
              double initialTemperature, double minimumTemperature, TerminalCondition terminalCondition) {
        super(terminalCondition);
        if (neighboringFunctions.isEmpty())
            throw new IllegalArgumentException("SA needs at least one neighboring function");
        this.neighboringFunctions = neighboringFunctions;
        this.coolingSchedule = coolingSchedule;
        this.initialTemperature = initialTemperature;
        this.minimumTemperature = minimumTemperature;
    }

    public void setInnerLoopLength(int innerLoopLength) {
        this.innerLoopLength = innerLoopLength;
    }

    @Override
    protected void init(OptimizationProblem problem) {
        super.init(problem);
        coolingSchedule.init();
        currentTemperature = initialTemperature;
    }

    @Override
    protected void search(OptimizationProblem problem) {
        while (currentTemperature > minimumTemperature && !isTerminated()) {
            for (int i = 0; i < innerLoopLength && !isTerminated(); i++) {
                MutationOperator nf = neighboringFunctions.get(RandUtil.randInt(neighboringFunctions.size()));
                Individual neighbor = neighbor(problem, nf, currentSolution);

                if (accepted(currentSolution.getCost(), neighbor.getCost()))
                    currentSolution = neighbor;

                iterationCount++;
                if (hasListeners())
                    fireIterationEvent(new SAIterationEvent(iterationCount, getNeighboringCount(), currentTemperature,
                            getBestKnownCost(), getBestKnownSolution(),
                            currentSolution.getCost(), currentSolution.getRepresentation()));
            }
            currentTemperature = coolingSchedule.updateTemperature(currentTemperature);
        }
    }

    private boolean accepted(double oldCost, double newCost) {
        if (newCost < oldCost)
            return true;
        return RandUtil.rollDice(Math.exp((oldCost - newCost) / currentTemperature));
    }

    @Override
    protected String defaultName() {
        return "SA";
    }
}
