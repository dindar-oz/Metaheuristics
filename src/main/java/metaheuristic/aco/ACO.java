package metaheuristic.aco;

import base.InitialSolutionGenerator;
import base.OptimizationProblem;
import base.TerminalCondition;
import metaheuristic.AbstractMetaheuristic;
import metaheuristic.BaseSIterationEvent;
import representation.IntegerPermutation;
import util.random.RandUtil;

/**
 * Ant System for GraphProblems. Each iteration every ant builds a tour, choosing the next node j
 * with probability proportional to pheromone(i,j)^alpha * (1/distance(i,j))^beta.
 * Then pheromone evaporates by rho and every ant deposits q/tourCost on the edges of its tour.
 * The InitialSolutionGenerator is not used; ants construct their own solutions.
 */
public class ACO extends AbstractMetaheuristic {

    private static final double MIN_DISTANCE = 1e-10;

    private final int antCount;
    private final double alpha;
    private final double beta;
    private final double rho;
    private final double q;

    private double[][] pheromone;

    public ACO(int antCount, double alpha, double beta, double rho, double q, TerminalCondition tc) {
        super(tc);
        this.antCount = antCount;
        this.alpha = alpha;
        this.beta = beta;
        this.rho = rho;
        this.q = q;
    }

    public ACO(int antCount, TerminalCondition tc) {
        this(antCount, 1, 2, 0.5, 1, tc);
    }

    @Override
    protected void run(OptimizationProblem problem, InitialSolutionGenerator solutionGenerator) {
        if (!(problem instanceof GraphProblem))
            throw new IllegalArgumentException("ACO requires a GraphProblem.");
        GraphProblem graph = (GraphProblem) problem;
        int n = graph.getNodeCount();

        pheromone = new double[n][n];
        for (double[] row : pheromone)
            java.util.Arrays.fill(row, 1.0);

        while (!isTerminated()) {
            int[][] tours = new int[antCount][];
            double[] costs = new double[antCount];
            for (int a = 0; a < antCount; a++) {
                tours[a] = constructTour(graph);
                costs[a] = evaluate(problem, new IntegerPermutation(tours[a]));
            }

            evaporate();
            for (int a = 0; a < antCount; a++)
                deposit(tours[a], q / Math.max(costs[a], MIN_DISTANCE));

            iterationCount++;
            if (hasListeners())
                fireIterationEvent(new BaseSIterationEvent(iterationCount, getNeighboringCount(),
                        getBestKnownCost(), getBestKnownSolution()));
        }
    }

    private int[] constructTour(GraphProblem graph) {
        int n = graph.getNodeCount();
        int[] tour = new int[n];
        boolean[] visited = new boolean[n];
        double[] weights = new double[n];

        tour[0] = RandUtil.randInt(n);
        visited[tour[0]] = true;

        for (int step = 1; step < n; step++) {
            int current = tour[step - 1];
            double total = 0;
            for (int j = 0; j < n; j++) {
                weights[j] = visited[j] ? 0 :
                        Math.pow(pheromone[current][j], alpha)
                                * Math.pow(1.0 / Math.max(graph.getDistance(current, j), MIN_DISTANCE), beta);
                total += weights[j];
            }
            int next = selectNext(weights, total, visited);
            tour[step] = next;
            visited[next] = true;
        }
        return tour;
    }

    private int selectNext(double[] weights, double total, boolean[] visited) {
        double r = RandUtil.randDouble() * total;
        int last = -1;
        for (int j = 0; j < weights.length; j++) {
            if (visited[j])
                continue;
            last = j;
            r -= weights[j];
            if (r <= 0)
                return j;
        }
        return last; // floating point rounding
    }

    private void evaporate() {
        for (double[] row : pheromone)
            for (int j = 0; j < row.length; j++)
                row[j] *= (1 - rho);
    }

    private void deposit(int[] tour, double amount) {
        for (int i = 0; i < tour.length; i++) {
            int from = tour[i];
            int to = tour[(i + 1) % tour.length];
            pheromone[from][to] += amount;
            pheromone[to][from] += amount;
        }
    }

    @Override
    protected String defaultName() {
        return "ACO";
    }
}
