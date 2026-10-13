package metaheuristic.termination;

import metaheuristic.run.Progress;

/** Stops after exactly the given number of objective function evaluations. */
public final class EvaluationBasedTC implements TerminalCondition {
    private final long maxEvaluations;

    public EvaluationBasedTC(long maxEvaluations) {
        this.maxEvaluations = maxEvaluations;
    }

    @Override
    public boolean isSatisfied(Progress p) {
        return p.evaluations() >= maxEvaluations;
    }
}
