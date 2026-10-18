package experimentrunner;

import java.util.ArrayList;
import java.util.List;

/**
 * worker.json: the heartbeat of the worker, written by it about every second. A worker whose heartbeat is older
 * than {@link #STALE_MILLIS} and that no longer holds worker.lock has died.
 */
public final class WorkerInfo {

    public static final long STALE_MILLIS = 15_000;

    public long pid;
    public String state;            // running, stopping, stopped
    public long started;
    public long heartbeat;
    public int threads;
    public String runtime;          // the code snapshot the worker runs from
    public List<Running> running = new ArrayList<>();

    public static final class Running {
        public String experiment;
        public String run;
        public long seed;
        public long started;
        public long iterations;
        public long evaluations;
        public double bestCost;
    }

    public boolean fresh(long now) {
        return "running".equals(state) || "stopping".equals(state) ? now - heartbeat < STALE_MILLIS : false;
    }
}
