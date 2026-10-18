package core.algorithm.aco.problem.tsp;

import core.algorithm.SimpleSolution;
import core.algorithm.aco.BaseAnt;
import core.algorithm.aco.PheromoneMatrix;
import core.algorithm.aco.PheromoneTrails;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.problems.tsp.TSP;
import core.representation.Permutation;
import core.utils.random.RNG;
import core.utils.random.RandUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Ant System tour construction for the TSP.
 * <p>
 * Standing at city {@code i}, the ant moves to an unvisited city {@code j} with probability
 * proportional to {@code tau(i,j)^alpha * eta(i,j)^beta}, where {@code eta = 1 / d(i,j)} is the
 * greedy desirability of the edge. {@code alpha} weighs the colony's experience and
 * {@code beta} the distance heuristic.
 * <p>
 * Works with any {@link PheromoneMatrix} laid out as {@code n x n} (e.g.
 * {@link TSPPheromoneMatrix} or {@link TSPQPheromoneMatrix}).
 */
public class TSPAnt extends BaseAnt {

    public static final double DEFAULT_ALPHA = 1.0;
    public static final double DEFAULT_BETA = 2.0;

    /** Smallest distance used for eta so that duplicate cities do not produce infinity. */
    private static final double MIN_DISTANCE = 1e-10;

    /** eta^beta tables shared by all ants on the same instance (keyed weakly by the TSP). */
    private static final Map<TSP, Map<Double, double[]>> HEURISTIC_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final double alpha;
    private final double beta;

    private TSP tsp;
    private int n;
    private PheromoneMatrix pheromone;
    private double[] etaBeta;          // flat n*n table of eta(i,j)^beta

    // Construction state, reused between tours to avoid garbage.
    private int[] tour;
    private int tourSize;
    private boolean[] visited;
    private int[] candidates;
    private int[] cellIndices;
    private double[] weights;

    public TSPAnt() {
        this(DEFAULT_ALPHA, DEFAULT_BETA);
    }

    /**
     * @param alpha pheromone exponent (1 is the usual choice)
     * @param beta  heuristic exponent (typically 2-5; 0 ignores distances)
     */
    public TSPAnt(double alpha, double beta) {
        this.alpha = alpha;
        this.beta = beta;
    }

    @Override
    public void init(OptimizationProblem problem, PheromoneTrails pheromoneTrails, RNG rng) {
        super.init(problem, pheromoneTrails, rng);
        this.tsp = (TSP) problem.model();
        this.n = tsp.getN();
        this.pheromone = requireMatrix(pheromoneTrails, TSPAnt.class);
        this.etaBeta = heuristicTable(tsp, beta);

        tour = new int[n];
        visited = new boolean[n];
        candidates = new int[n];
        cellIndices = new int[n];
        weights = new double[n];
    }

    @Override
    protected void reset() {
        Arrays.fill(visited, false);
        tourSize = 0;
        visit(rng.randInt(n));
    }

    @Override
    protected void next() {
        int current = tour[tourSize - 1];

        int count = 0;
        for (int c = 0; c < n; c++) {
            if (!visited[c]) {
                candidates[count] = c;
                cellIndices[count] = pheromone.index(current, c);
                count++;
            }
        }

        // One consistent snapshot of all candidate pheromone values for this step.
        pheromone.read(cellIndices, count, weights);

        int row = current * n;
        for (int i = 0; i < count; i++) {
            double tau = alpha == 1.0 ? weights[i] : Math.pow(weights[i], alpha);
            weights[i] = tau * etaBeta[row + candidates[i]];
        }

        visit(candidates[RandUtils.rouletteSelect(rng, weights, count)]);
    }

    private void visit(int city) {
        visited[city] = true;
        tour[tourSize++] = city;
    }

    @Override
    protected boolean solutionConstructed() {
        return tourSize >= n;
    }

    @Override
    protected Solution buildSolution() {
        Permutation permutation = new Permutation(Arrays.copyOf(tour, n));
        return new SimpleSolution(permutation, problem.objectiveValues(permutation));
    }

    /** Returns (and caches) the flat {@code eta(i,j)^beta} table for an instance. */
    private static double[] heuristicTable(TSP tsp, double beta) {
        Map<Double, double[]> byBeta;
        synchronized (HEURISTIC_CACHE) {
            byBeta = HEURISTIC_CACHE.computeIfAbsent(tsp, t -> new HashMap<>());
        }
        synchronized (byBeta) {
            return byBeta.computeIfAbsent(beta, b -> {
                int n = tsp.getN();
                double[] table = new double[n * n];
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        double eta = 1.0 / Math.max(tsp.getDistance(i, j), MIN_DISTANCE);
                        table[i * n + j] = b == 0 ? 1.0 : Math.pow(eta, b);
                    }
                }
                return table;
            });
        }
    }
}
