package metaheuristic.run;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helpers for building the metrics map reported with SearchContext.iterationCompleted().
 */
public final class Metrics {

    public static final String CURRENT_COST = "currentCost";
    public static final String MEAN_COST = "meanCost";

    private Metrics() {
    }

    public static Map<String, Double> of(String k1, double v1) {
        return Collections.singletonMap(k1, v1);
    }

    public static Map<String, Double> of(String k1, double v1, String k2, double v2) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return Collections.unmodifiableMap(m);
    }

    public static Map<String, Double> of(String k1, double v1, String k2, double v2, String k3, double v3) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return Collections.unmodifiableMap(m);
    }
}
