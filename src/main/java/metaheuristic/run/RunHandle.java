package metaheuristic.run;

import representation.base.Representation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe handle to a run: lets other threads cancel it, poll its latest progress and wait for its result.
 */
public final class RunHandle<S extends Representation<S>> {
    private volatile boolean cancelled = false;
    private final AtomicReference<ProgressSnapshot> latest = new AtomicReference<>();
    private final CompletableFuture<RunResult<S>> result = new CompletableFuture<>();

    RunHandle() {
    }

    // asks the run to stop; it ends at its next shouldStop()/evaluate() call with StopReason.CANCELLED
    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    // the most recently published progress, or null if the run has not completed an iteration yet
    public ProgressSnapshot latestProgress() {
        return latest.get();
    }

    public CompletableFuture<RunResult<S>> result() {
        return result;
    }

    public boolean isDone() {
        return result.isDone();
    }

    void publish(ProgressSnapshot snapshot) {
        latest.set(snapshot);
    }

    void complete(RunResult<S> r) {
        result.complete(r);
    }

    void fail(Throwable t) {
        result.completeExceptionally(t);
    }
}
