package lab.ui;

import lab.model.ParamValues;

/**
 * An algorithm with its parameter values, as configured in the lab. Several configs may use the same algorithm.
 */
public final class RunConfig {
    final String algorithm;
    final ParamValues values;
    String label;

    RunConfig(String algorithm, String label, ParamValues values) {
        this.algorithm = algorithm;
        this.label = label;
        this.values = values;
    }

    @Override
    public String toString() {
        return label;
    }
}
