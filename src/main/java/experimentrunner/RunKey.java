package experimentrunner;

/**
 * One run of an experiment: a configuration on an instance, at a repeat. The seed follows from it, so the same key
 * always gives the same run.
 */
public record RunKey(String config, String instance, int repeat) {

    @Override
    public String toString() {
        return config + "," + instance + "," + repeat;
    }
}
