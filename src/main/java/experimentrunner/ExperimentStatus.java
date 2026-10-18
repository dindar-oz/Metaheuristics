package experimentrunner;

/**
 * status.json of an experiment: its progress, written by the worker when a run finishes or starts.
 */
public final class ExperimentStatus {
    public int total;
    public int done;
    public int running;
    public int failures;
    public long updated;
    public double meanRunMillis;    // of the runs finished so far
    public long etaMillis;          // of the runs left, at the current number of threads; -1 if unknown
}
