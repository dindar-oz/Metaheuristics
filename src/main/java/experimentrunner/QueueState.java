package experimentrunner;

import java.util.ArrayList;
import java.util.List;

/**
 * queue.json: the experiments in the order the worker takes them, and the worker's settings. Changed only through
 * {@link Workspace#update}, under the workspace's lock, by the CLI, the GUI and the worker alike.
 */
public final class QueueState {

    public enum State {
        QUEUED,     // runs when its turn comes
        PAUSED,     // takes no new runs; with interrupt, its runs in flight were stopped (and will run again)
        DONE,       // every run finished
        CANCELLED,  // stopped for good; its results are kept
        FAILED      // stopped because its runs kept failing; see message
    }

    public enum Stop { AFTER_RUNS, NOW }

    public int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    public boolean paused;              // no new runs for any experiment
    public Stop stopWorker;             // asks the worker to exit; cleared when the next worker starts
    public List<Entry> experiments = new ArrayList<>();

    public static final class Entry {
        public String id;
        public State state = State.QUEUED;
        public boolean interrupt;       // with PAUSED: also stop the runs in flight
        public String submitted;
        public String finished;
        public String message;
    }

    public Entry entry(String id) {
        return experiments.stream().filter(e -> e.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no experiment " + id));
    }

    public boolean has(String id) {
        return experiments.stream().anyMatch(e -> e.id.equals(id));
    }
}
