package metaheuristic.run;

import representation.base.Representation;

/**
 * Immutable outcome of one run. Together with the algorithm description and the seed it is enough to reproduce the run.
 */
public final class RunResult<S extends Representation<S>> {
    private final RunInfo info;
    private final ProgressSnapshot finalProgress;
    private final StopReason stopReason;
    private final S bestSolution; // never modified; copied on access

    RunResult(RunInfo info, ProgressSnapshot finalProgress, StopReason stopReason, S bestSolution) {
        this.info = info;
        this.bestSolution = bestSolution;
        this.finalProgress = finalProgress;
        this.stopReason = stopReason;
    }

    public RunInfo info() {
        return info;
    }

    public long seed() {
        return info.seed();
    }

    public StopReason stopReason() {
        return stopReason;
    }

    public ProgressSnapshot finalProgress() {
        return finalProgress;
    }

    // a copy of the best solution found, or null if nothing was evaluated
    public S bestSolution() {
        return bestSolution == null ? null : bestSolution.copy();
    }

    public double bestCost() {
        return finalProgress.bestCost();
    }

    public long evaluations() {
        return finalProgress.evaluations();
    }

    public long iterations() {
        return finalProgress.iterations();
    }

    public long elapsedMillis() {
        return finalProgress.elapsedMillis();
    }

    public long timeToBestMillis() {
        return finalProgress.timeToBestMillis();
    }

    @Override
    public String toString() {
        return info + ": best=" + bestCost() + " evals=" + evaluations() + " iterations=" + iterations()
                + " time=" + elapsedMillis() + "ms stop=" + stopReason;
    }
}
