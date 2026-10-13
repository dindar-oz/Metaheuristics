package metaheuristic.listener;

import metaheuristic.run.ProgressSnapshot;
import metaheuristic.run.RunInfo;
import metaheuristic.run.RunResult;
import metaheuristic.run.SearchListener;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Forwards at most one onProgress/onImprovement per interval; onStart and onFinish are always forwarded.
 * Thread-safe; when shared by concurrent runs they are throttled together, so prefer one instance per run.
 */
public final class ThrottledListener implements SearchListener {
    private final SearchListener delegate;
    private final long intervalNanos;
    private final AtomicLong lastProgress = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong lastImprovement = new AtomicLong(Long.MIN_VALUE);

    public ThrottledListener(SearchListener delegate, long intervalMillis) {
        this.delegate = delegate;
        this.intervalNanos = intervalMillis * 1_000_000L;
    }

    @Override
    public void onStart(RunInfo info) {
        delegate.onStart(info);
    }

    @Override
    public void onProgress(ProgressSnapshot progress) {
        if (due(lastProgress))
            delegate.onProgress(progress);
    }

    @Override
    public void onImprovement(ProgressSnapshot progress) {
        if (due(lastImprovement))
            delegate.onImprovement(progress);
    }

    @Override
    public void onFinish(RunResult<?> result) {
        delegate.onFinish(result);
    }

    private boolean due(AtomicLong last) {
        long now = System.nanoTime();
        long previous = last.get();
        return (previous == Long.MIN_VALUE || now - previous >= intervalNanos) && last.compareAndSet(previous, now);
    }
}
