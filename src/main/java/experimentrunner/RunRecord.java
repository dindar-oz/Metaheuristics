package experimentrunner;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One finished run: a line of runs.jsonl. {@code metrics} are the algorithm's last metrics (e.g. branchingFactor,
 * diversity); {@code runtime} names the code snapshot that ran it.
 */
public final class RunRecord {
    public String config;
    public String instance;
    public int repeat;
    public long seed;
    public double bestCost;
    public long evaluations;
    public long iterations;
    public long elapsedMs;
    public long timeToBestMs;
    public String stopReason;
    public Map<String, Double> metrics = new LinkedHashMap<>();
    public String runtime;
    public long finished;

    public RunKey key() {
        return new RunKey(config, instance, repeat);
    }

    /** A line of trace.jsonl: the progress of a run at one iteration. */
    public static final class Trace {
        public String config;
        public String instance;
        public int repeat;
        public long iteration;
        public long evaluations;
        public long elapsedMs;
        public double bestCost;
        public Map<String, Double> metrics = new LinkedHashMap<>();
    }
}
