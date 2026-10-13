package metaheuristic.listener;

import metaheuristic.run.ProgressSnapshot;
import metaheuristic.run.RunInfo;
import metaheuristic.run.RunResult;
import metaheuristic.run.SearchListener;

import java.io.PrintStream;

/**
 * Prints run start, progress and result lines. Usually wrapped with Listeners.throttled().
 */
public final class ConsoleProgressListener implements SearchListener {
    private final PrintStream out;

    public ConsoleProgressListener() {
        this(System.out);
    }

    public ConsoleProgressListener(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onStart(RunInfo info) {
        out.println("[start] " + info);
    }

    @Override
    public void onProgress(ProgressSnapshot progress) {
        out.println("[" + Thread.currentThread().getName() + "] " + progress);
    }

    @Override
    public void onFinish(RunResult<?> result) {
        out.println("[done] " + result);
    }
}
