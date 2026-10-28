package metaheuristic.run;

import representation.base.Representation;

import java.util.HashSet;
import java.util.List;

/**
 * The population diagnostics behind {@link Metrics#DUPLICATES} and {@link Metrics#DIVERSITY}, for any list of
 * solutions (e.g. the ants of an iteration or a population).
 */
public final class PopulationStats {

    public static final int DIVERSITY_SAMPLE = 16; // members whose pairwise distances give the diversity

    private PopulationStats() {
    }

    // the share of members equal to an earlier member; 0 for fewer than two members
    public static double duplicates(List<?> population) {
        int n = population.size();
        return n < 2 ? 0 : (double) (n - new HashSet<>(population).size()) / n;
    }

    /**
     * The mean distanceTo() over all pairs of an evenly spaced sample of up to {@value #DIVERSITY_SAMPLE} members, so
     * that large populations stay cheap; NaN for fewer than two members. Throws UnsupportedOperationException if the
     * representation defines no distance.
     */
    public static <S extends Representation<S>> double diversity(List<? extends S> population) {
        int n = population.size();
        if (n < 2)
            return Double.NaN;
        int k = Math.min(n, DIVERSITY_SAMPLE);
        double sum = 0;
        for (int i = 0; i < k; i++)
            for (int j = i + 1; j < k; j++)
                sum += population.get(i * n / k).distanceTo(population.get(j * n / k));
        return sum / (k * (k - 1) / 2.0);
    }
}
