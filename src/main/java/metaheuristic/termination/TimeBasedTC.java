package metaheuristic.termination;

import metaheuristic.run.Progress;

/** Stops after the given wall-clock time (milliseconds) since the start of the run. */
public final class TimeBasedTC implements TerminalCondition {
    private final long maxMillis;

    public TimeBasedTC(long maxMillis) {
        this.maxMillis = maxMillis;
    }

    @Override
    public boolean isSatisfied(Progress p) {
        return p.elapsedMillis() >= maxMillis;
    }
}
