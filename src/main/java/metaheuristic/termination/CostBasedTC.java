package metaheuristic.termination;

import metaheuristic.run.Progress;

/** Stops once the best cost reaches the target cost. */
public final class CostBasedTC implements TerminalCondition {
    private final double targetCost;

    public CostBasedTC(double targetCost) {
        this.targetCost = targetCost;
    }

    @Override
    public boolean isSatisfied(Progress p) {
        return p.bestCost() <= targetCost;
    }
}
