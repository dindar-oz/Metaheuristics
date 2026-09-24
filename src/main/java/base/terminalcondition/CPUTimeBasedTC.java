package base.terminalcondition;

import base.TerminalCondition;
import metaheuristic.MetaHeuristic;

/** Stops after the given wall-clock time (milliseconds) since init(). */
public class CPUTimeBasedTC implements TerminalCondition {
    private final long maxMillis;
    private long startTime;

    public CPUTimeBasedTC(long maxMillis) {
        this.maxMillis = maxMillis;
    }

    @Override
    public boolean isSatisfied(MetaHeuristic alg) {
        return System.currentTimeMillis() - startTime > maxMillis;
    }

    @Override
    public void init() {
        startTime = System.currentTimeMillis();
    }
}
