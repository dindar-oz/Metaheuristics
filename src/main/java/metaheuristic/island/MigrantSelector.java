package metaheuristic.island;

import metaheuristic.Params;
import metaheuristic.run.PopulationStats;
import representation.base.Individual;
import representation.base.Representation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which of the solutions a source island offers a channel of an {@link IslandModel} sends. Solutions that the target
 * already holds, and repeats, are never sent. Stateless and deterministic: ties go to the cheaper solution, then to
 * the one offered first.
 */
public interface MigrantSelector {

    // at most `count` of the offered solutions; empty if none is worth sending
    <S extends Representation<S>> List<Individual<S>> select(List<Individual<S>> offered, List<S> residents);

    // the `count` cheapest
    static MigrantSelector best(int count) {
        return new Best(Params.positive("count", count));
    }

    /**
     * Up to `count` good solutions that differ from the target and from each other: the cheapest first, then, among
     * those at most the fraction `maxGap` costlier than the cheapest, each time the one farthest (distanceTo) from the
     * target's residents and from those chosen so far, measured as the distance to the nearest of them. Residents are
     * represented by an evenly spaced sample of up to {@value PopulationStats#DIVERSITY_SAMPLE} distinct ones. Without
     * a distance, the cheapest.
     */
    static MigrantSelector diverse(int count, double maxGap) {
        return new Diverse(Params.positive("count", count), Params.nonNegative("maxGap", maxGap));
    }

    record Best(int count) implements MigrantSelector {
        @Override
        public <S extends Representation<S>> List<Individual<S>> select(List<Individual<S>> offered, List<S> residents) {
            List<Individual<S>> candidates = candidates(offered, residents);
            return List.copyOf(candidates.subList(0, Math.min(count, candidates.size())));
        }
    }

    record Diverse(int count, double maxGap) implements MigrantSelector {
        @Override
        public <S extends Representation<S>> List<Individual<S>> select(List<Individual<S>> offered, List<S> residents) {
            List<Individual<S>> candidates = candidates(offered, residents);
            if (candidates.isEmpty())
                return List.of();
            double limit = candidates.get(0).getCost() + maxGap * Math.abs(candidates.get(0).getCost());
            candidates.removeIf(c -> c.getCost() > limit);
            try {
                return spread(candidates, sample(residents));
            } catch (UnsupportedOperationException e) {
                return List.copyOf(candidates.subList(0, Math.min(count, candidates.size())));
            }
        }

        // the cheapest, then greedily the farthest from everything held or chosen
        private <S extends Representation<S>> List<Individual<S>> spread(List<Individual<S>> candidates, List<S> held) {
            List<Individual<S>> chosen = new ArrayList<>();
            double[] nearest = new double[candidates.size()];      // distance to the nearest held or chosen solution
            Arrays.fill(nearest, Double.POSITIVE_INFINITY);
            for (S h : held)
                for (int c = 0; c < candidates.size(); c++)
                    nearest[c] = Math.min(nearest[c], candidates.get(c).getRepresentation().distanceTo(h));
            boolean[] taken = new boolean[candidates.size()];
            int next = 0;                                          // the cheapest goes first
            while (next >= 0 && chosen.size() < count) {
                Individual<S> pick = candidates.get(next);
                chosen.add(pick);
                taken[next] = true;
                next = -1;
                for (int c = 0; c < candidates.size(); c++) {
                    if (taken[c])
                        continue;
                    nearest[c] = Math.min(nearest[c], candidates.get(c).getRepresentation().distanceTo(pick.getRepresentation()));
                    if (next < 0 || nearest[c] > nearest[next])
                        next = c;
                }
            }
            return List.copyOf(chosen);
        }

        // up to DIVERSITY_SAMPLE distinct residents, evenly spaced
        private static <S> List<S> sample(List<S> residents) {
            List<S> distinct = new ArrayList<>(new LinkedHashSet<>(residents));
            int n = distinct.size(), k = Math.min(n, PopulationStats.DIVERSITY_SAMPLE);
            List<S> sample = new ArrayList<>(k);
            for (int i = 0; i < k; i++)
                sample.add(distinct.get(i * n / k));
            return sample;
        }
    }

    // the offered solutions by cost (stable), without repeats and without what the target holds
    private static <S extends Representation<S>> List<Individual<S>> candidates(List<Individual<S>> offered,
                                                                               List<S> residents) {
        List<Individual<S>> sorted = new ArrayList<>(offered);
        sorted.sort(Comparator.comparingDouble(Individual::getCost));
        Set<S> seen = new HashSet<>(residents);
        List<Individual<S>> candidates = new ArrayList<>();
        for (Individual<S> i : sorted)
            if (seen.add(i.getRepresentation()))
                candidates.add(i);
        return candidates;
    }
}
