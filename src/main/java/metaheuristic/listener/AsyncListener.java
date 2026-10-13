package metaheuristic.listener;

import metaheuristic.run.ProgressSnapshot;
import metaheuristic.run.RunInfo;
import metaheuristic.run.RunResult;
import metaheuristic.run.SearchListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Delivers callbacks to the delegate on its own background thread, so a slow delegate (file, network, GUI)
 * never slows down the search. Progress and improvement events are coalesced: if the delegate falls behind,
 * it only sees the latest one. onStart/onFinish are always delivered, in order.
 * The delegate is called from a single thread, so it needs no synchronization of its own.
 * An AsyncListener serves ONE run: its thread ends after onFinish is delivered, so create one per run
 * with Runner/Experiment.listenerPerRun(info -> Listeners.async(...)).
 */
public final class AsyncListener implements SearchListener, AutoCloseable {
    private final SearchListener delegate;
    private final ExecutorService executor;
    private final AtomicReference<ProgressSnapshot> pendingProgress = new AtomicReference<>();
    private final AtomicReference<ProgressSnapshot> pendingImprovement = new AtomicReference<>();

    public AsyncListener(SearchListener delegate) {
        this.delegate = delegate;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "async-listener");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void onStart(RunInfo info) {
        executor.execute(() -> delegate.onStart(info));
    }

    @Override
    public void onProgress(ProgressSnapshot progress) {
        if (pendingProgress.getAndSet(progress) == null)
            executor.execute(() -> delegate.onProgress(pendingProgress.getAndSet(null)));
    }

    @Override
    public void onImprovement(ProgressSnapshot progress) {
        if (pendingImprovement.getAndSet(progress) == null)
            executor.execute(() -> delegate.onImprovement(pendingImprovement.getAndSet(null)));
    }

    @Override
    public void onFinish(RunResult<?> result) {
        executor.execute(() -> delegate.onFinish(result));
        executor.shutdown();
    }

    // Stops the background thread after the queued callbacks have been delivered (done automatically on finish)
    @Override
    public void close() {
        executor.shutdown();
    }
}
