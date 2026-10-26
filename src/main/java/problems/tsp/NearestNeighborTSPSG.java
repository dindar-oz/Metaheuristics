package problems.tsp;

import base.InitialSolutionGenerator;
import base.OptimizationProblem;
import representation.IntegerPermutation;
import util.random.RNG;

/**
 * Nearest-neighbor tours: from a random first city, always on to the nearest city not visited yet (ties: the lower
 * index). The random start makes the tours differ, e.g. across an EA population. Stateless; safe to share between
 * concurrent runs.
 */
public final class NearestNeighborTSPSG implements InitialSolutionGenerator<IntegerPermutation> {

    private final TSPProblem tsp;

    public NearestNeighborTSPSG(TSPProblem tsp) {
        this.tsp = tsp;
    }

    @Override
    public IntegerPermutation generate(OptimizationProblem<IntegerPermutation> ignored, RNG rng) {
        int n = tsp.getCityCount();
        int[] tour = new int[n];
        boolean[] visited = new boolean[n];
        tour[0] = rng.randInt(n);
        visited[tour[0]] = true;
        for (int k = 1; k < n; k++) {
            int from = tour[k - 1], next = -1;
            for (int c = 0; c < n; c++)
                if (!visited[c] && (next < 0 || tsp.getDistance(from, c) < tsp.getDistance(from, next)))
                    next = c;
            tour[k] = next;
            visited[next] = true;
        }
        return new IntegerPermutation(tour);
    }

    @Override
    public String toString() {
        return "NearestNeighborTSPSG";
    }
}
