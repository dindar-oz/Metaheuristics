package metaheuristic.termination;

import metaheuristic.run.Progress;

/** Stops when the best cost has not improved for the given number of evaluations. */
public final class StagnationTC implements TerminalCondition {
    private final long maxEvaluationsWithoutImprovement;

    public StagnationTC(long maxEvaluationsWithoutImprovement) {
        this.maxEvaluationsWithoutImprovement = maxEvaluationsWithoutImprovement;
    }

    @Override
    public boolean isSatisfied(Progress p) {
        return p.evaluations() > 0 && p.evaluationsSinceImprovement() >= maxEvaluationsWithoutImprovement;
    }
}
