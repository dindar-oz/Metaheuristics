package base.terminalcondition;

import base.TerminalCondition;
import metaheuristic.MetaHeuristic;

/** Stops once the best known cost reaches the target cost. */
public class CostBasedTC implements TerminalCondition {
    private final double targetCost;

    public CostBasedTC(double targetCost) {
        this.targetCost = targetCost;
    }

    @Override
    public boolean isSatisfied(MetaHeuristic alg) {
        return alg.getBestKnownCost() <= targetCost;
    }
}
