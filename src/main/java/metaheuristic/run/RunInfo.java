package metaheuristic.run;

/**
 * What is being run: passed to listeners when a run starts.
 */
public final class RunInfo {
    private final String algorithm;
    private final String problem;
    private final long seed;

    RunInfo(String algorithm, String problem, long seed) {
        this.algorithm = algorithm;
        this.problem = problem;
        this.seed = seed;
    }

    // Metaheuristic.describe(): name and parameters
    public String algorithm() {
        return algorithm;
    }

    public String problem() {
        return problem;
    }

    public long seed() {
        return seed;
    }

    @Override
    public String toString() {
        return algorithm + " on " + problem + " (seed " + seed + ")";
    }
}
