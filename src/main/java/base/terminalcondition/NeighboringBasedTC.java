package base.terminalcondition;

import base.TerminalCondition;
import metaheuristic.MetaHeuristic;

/** Stops after the given number of objective function evaluations. */
public class NeighboringBasedTC implements TerminalCondition {
    private final long maxNeighboring;

    public NeighboringBasedTC(long maxNeighboring) {
        this.maxNeighboring = maxNeighboring;
    }

    @Override
    public boolean isSatisfied(MetaHeuristic alg) {
        return alg.getNeighboringCount() >= maxNeighboring;
    }
}
