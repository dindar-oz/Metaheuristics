package metaheuristic.termination;

import metaheuristic.run.Progress;

/** Stops after the given number of iterations. */
public final class IterationBasedTC implements TerminalCondition {
    private final long maxIterations;

    public IterationBasedTC(long maxIterations) {
        this.maxIterations = maxIterations;
    }

    @Override
    public boolean isSatisfied(Progress p) {
        return p.iterations() >= maxIterations;
    }
}
