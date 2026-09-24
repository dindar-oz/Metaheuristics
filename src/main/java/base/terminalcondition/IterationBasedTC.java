package base.terminalcondition;

import base.TerminalCondition;
import metaheuristic.MetaHeuristic;

/** Stops after the given number of iterations. */
public class IterationBasedTC implements TerminalCondition {
    private final long maxIteration;

    public IterationBasedTC(long maxIteration) {
        this.maxIteration = maxIteration;
    }

    @Override
    public boolean isSatisfied(MetaHeuristic alg) {
        return alg.getIterationCount() >= maxIteration;
    }
}
