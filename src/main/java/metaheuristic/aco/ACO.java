package metaheuristic.aco;

import metaheuristic.AbstractMetaheuristic;
import metaheuristic.Params;
import metaheuristic.run.Metrics;
import metaheuristic.run.SearchContext;
import representation.IntegerPermutation;
import util.random.RNG;

import java.util.Arrays;

/**
 * Ant System for GraphProblems. Each iteration every ant builds a tour, choosing the next node j
 * with probability proportional to pheromone(i,j)^alpha * (1/distance(i,j))^beta.
 * Then pheromone evaporates by rho and every ant deposits q/tourCost on the edges of its tour.
 * Ants construct their own solutions, so no InitialSolutionGenerator is needed.
 */
public final class ACO extends AbstractMetaheuristic<IntegerPermutation, GraphProblem> {

    private static final double MIN_DISTANCE = 1e-10;

    private final int antCount;
    private final double alpha;
    private final double beta;
    private final double rho;
    private final double q;

    private ACO(Builder b) {
        super(b.name);
        this.antCount = Params.positive("antCount", b.antCount);
        this.alpha = Params.nonNegative("alpha", b.alpha);
        this.beta = Params.nonNegative("beta", b.beta);
        this.rho = Params.probability("rho", b.rho);
        this.q = Params.positive("q", b.q);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void search(GraphProblem graph, SearchContext<IntegerPermutation> ctx) {
        RNG rng = ctx.rng();
        int n = graph.getNodeCount();

        double[][] pheromone = new double[n][n];
        for (double[] row : pheromone)
            Arrays.fill(row, 1.0);
        // (1/distance)^beta never changes; pheromone^alpha * heuristic is computed once per iteration, not per ant
        double[][] heuristic = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                heuristic[i][j] = Math.pow(1.0 / Math.max(graph.getDistance(i, j), MIN_DISTANCE), beta);
        double[][] attractiveness = new double[n][n];

        while (!ctx.shouldStop()) {
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    attractiveness[i][j] = Math.pow(pheromone[i][j], alpha) * heuristic[i][j];
            int[][] tours = new int[antCount][];
            double[] costs = new double[antCount];
            double sum = 0;
            for (int a = 0; a < antCount; a++) {
                if (ctx.shouldStop())
                    return;
                tours[a] = constructTour(n, attractiveness, rng);
                costs[a] = ctx.evaluate(new IntegerPermutation(tours[a]));
                sum += costs[a];
            }

            for (double[] row : pheromone)
                for (int j = 0; j < n; j++)
                    row[j] *= (1 - rho);
            for (int a = 0; a < antCount; a++)
                deposit(pheromone, tours[a], q / Math.max(costs[a], MIN_DISTANCE));

            ctx.iterationCompleted(Metrics.of(Metrics.MEAN_COST, sum / antCount));
        }
    }

    private static int[] constructTour(int n, double[][] attractiveness, RNG rng) {
        int[] tour = new int[n];
        boolean[] visited = new boolean[n];
        double[] weights = new double[n];

        tour[0] = rng.randInt(n);
        visited[tour[0]] = true;

        for (int step = 1; step < n; step++) {
            int current = tour[step - 1];
            for (int j = 0; j < n; j++)
                weights[j] = visited[j] ? 0 : attractiveness[current][j];
            int next = rng.rouletteSelect(weights);
            if (visited[next]) // rounding error selected a zero-weight entry
                next = firstUnvisited(visited);
            tour[step] = next;
            visited[next] = true;
        }
        return tour;
    }

    private static int firstUnvisited(boolean[] visited) {
        for (int j = 0; j < visited.length; j++)
            if (!visited[j])
                return j;
        throw new IllegalStateException("all nodes visited");
    }

    private static void deposit(double[][] pheromone, int[] tour, double amount) {
        for (int i = 0; i < tour.length; i++) {
            int from = tour[i];
            int to = tour[(i + 1) % tour.length];
            pheromone[from][to] += amount;
            pheromone[to][from] += amount;
        }
    }

    @Override
    protected String parameters() {
        return "ants=" + antCount + ", alpha=" + alpha + ", beta=" + beta + ", rho=" + rho + ", q=" + q;
    }

    public static final class Builder {
        private String name = "ACO";
        private int antCount = 20;
        private double alpha = 1;
        private double beta = 2;
        private double rho = 0.5;
        private double q = 1;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        // default 20
        public Builder antCount(int antCount) {
            this.antCount = antCount;
            return this;
        }

        // pheromone influence; default 1
        public Builder alpha(double alpha) {
            this.alpha = alpha;
            return this;
        }

        // distance influence; default 2
        public Builder beta(double beta) {
            this.beta = beta;
            return this;
        }

        // evaporation rate in [0, 1]; default 0.5
        public Builder rho(double rho) {
            this.rho = rho;
            return this;
        }

        // pheromone deposit factor; default 1
        public Builder q(double q) {
            this.q = q;
            return this;
        }

        public ACO build() {
            return new ACO(this);
        }
    }
}
