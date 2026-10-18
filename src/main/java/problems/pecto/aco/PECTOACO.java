package problems.pecto.aco;

import base.MutationOperator;
import metaheuristic.AbstractMetaheuristic;
import metaheuristic.Params;
import metaheuristic.run.Metrics;
import metaheuristic.run.SearchContext;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.operators.GreedyPECTOSG;
import problems.pecto.operators.MoveTrip;
import problems.pecto.operators.OptimalHospitals;
import problems.pecto.operators.SwapTrips;
import util.random.MersenneTwisterRNG;
import util.random.RNG;
import util.random.Seeds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MAX-MIN Ant System for PECTO that builds the routes directly. An ant starts with every ambulance at its position and
 * no trips; each step moves one ambulance to one more patient, until every patient is transported:
 * <ol>
 * <li><b>Ambulance.</b> Every ambulance is rated by its most attractive next patient (below); the ambulance that moves
 *     is drawn uniformly from the {@code shortlist} best rated. Ambulances far from the remaining patients are
 *     rarely drawn, so they can do few trips or none.</li>
 * <li><b>Patient.</b> The candidates are the nearest patients of the ambulance's previous stop (the ambulance itself,
 *     or its last patient). An unserved candidate q is drawn with probability proportional to
 *     tau(prev, q)^alpha * eta^beta, eta = 1 / (w_q D(at, q)), where at is the hospital the ambulance is at. The
 *     pheromone is directed and learns which patient follows which. When no candidate is left, the nearest
 *     unserved patient is taken.</li>
 * <li><b>Hospital.</b> A patient's hospital changes only its delivery and the next pickup (Eq. 8), so it is drawn
 *     twice. On pickup, a provisional hospital is drawn with eta = 1 / (w_q D(q, h)); it holds a bed and is where
 *     the ambulance waits. When the ambulance's next patient r is chosen, the hospital is drawn again with
 *     eta = 1 / (w_q D(q, h) + w_r D(h, r)). Both draw with probability proportional to tau(q, h)^alpha * eta^beta
 *     among the {@code hospitalCandidates} nearest hospitals of q that have a free bed, so every ant builds a
 *     feasible solution.</li>
 * </ol>
 * After each iteration the best ant is improved by {@code localSearchSteps} random {@link MoveTrip} / {@link SwapTrips}
 * neighbors (first improvement), then given the optimal hospitals for its routes ({@link OptimalHospitals}). It
 * deposits 1/cost on its patient and hospital choices, every {@value #GLOBAL_BEST_PERIOD}th iteration the best
 * solution so far does. Pheromone is kept in [tau_min, tau_max] with tau_max = 1 / (rho * best cost) and tau_min from
 * {@code pBest} (Stützle &amp; Hoos, 2000); it starts at the tau_max of the greedy solution ({@link GreedyPECTOSG}),
 * which is the first solution evaluated.
 * <p>
 * Every solution is evaluated through the search context, including the local search neighbors. Pheromone is stored
 * for the candidates only: (A + P) x candidates + P x H values. Without candidate lists most of the probability would
 * go to the many hospitals and patients that are neither near nor far, and the ants would build poor solutions.
 * <p>
 * <b>Explorer ants</b>, against early convergence. The last {@code explorers} ants of every iteration build like the
 * others, except that each of their patient and hospital draws ignores the pheromone with probability
 * {@code explorerRate}: every candidate then has pheromone weight 1 and the heuristic exponent is {@code explorerBeta}.
 * Their solutions stay close to what the colony learned, so they can be good, but differ from it. The best explorer
 * of the iteration deposits {@code explorerDeposit} / cost on its choices, unless it is the solution that deposits
 * anyway; this keeps the pheromone from collapsing onto one solution. {@code tauMinRatio} raises tau_min, so that no
 * choice becomes practically impossible (off by default). The defaults come from tuning on the large Table I
 * instances (see the experiments' journals).
 * <p>
 * <b>Execution.</b> {@link ExecutionMode#SEQUENTIAL} builds the ants one after the other on the run's thread;
 * {@link ExecutionMode#SYNCHRONOUS} builds them in parallel on {@code threads} threads ({@link AntBuilds}).
 * Everything else (evaluations, local search, pheromone update) stays on the run's thread, in ant order. Every ant has
 * its own RNG, derived from the run's, so both modes give the same run for the same seed, whatever the number of
 * threads.
 * <p>
 * The parts: {@link CandidateLists} (what the ants know about the instance), {@link Pheromone}, {@link Ant} (the
 * construction) and {@link AntBuilds}; this class holds the parameters and the iterations.
 */
public final class PECTOACO extends AbstractMetaheuristic<PECTOSolution, PECTOProblem> {

    /** The lambda-branching factor of the patient pheromone, a metric of every iteration (see {@link Pheromone}). */
    public static final String BRANCHING_FACTOR = "branchingFactor";
    /** The number of iterations so far whose best ant was an explorer, a metric when there are explorers. */
    public static final String EXPLORER_WINS = "explorerWins";

    static final int GLOBAL_BEST_PERIOD = 5;
    static final double MIN_COST = 1e-10;
    private static final List<MutationOperator<PECTOSolution>> MOVES = Arrays.asList(new MoveTrip(), new SwapTrips());

    public enum ExecutionMode { SEQUENTIAL, SYNCHRONOUS }

    // read by the parts of the colony (Pheromone, Ant)
    final double alpha, beta, rho, pBest, tauMinRatio, explorerBeta;
    final int candidates, hospitalCandidates, shortlist;

    private final int antCount;
    private final int localSearchSteps;
    private final boolean optimalHospitals;
    private final ExecutionMode executionMode;
    private final int threads;
    private final int explorers;
    private final double explorerRate;
    private final double explorerDeposit;

    private PECTOACO(Builder b) {
        super(b.name);
        antCount = Params.positive("antCount", b.antCount);
        alpha = Params.nonNegative("alpha", b.alpha);
        beta = Params.nonNegative("beta", b.beta);
        rho = Params.positive("rho", Params.probability("rho", b.rho));                     // (0, 1]
        pBest = Params.below("pBest", Params.positive("pBest", b.pBest), 1);                 // (0, 1)
        candidates = Params.positive("candidates", b.candidates);
        hospitalCandidates = Params.positive("hospitalCandidates", b.hospitalCandidates);
        shortlist = Params.positive("shortlist", b.shortlist);
        localSearchSteps = Params.nonNegative("localSearchSteps", b.localSearchSteps);
        optimalHospitals = b.optimalHospitals;
        executionMode = Params.required("executionMode", b.executionMode);
        threads = Params.positive("threads", b.threads);
        tauMinRatio = Params.below("tauMinRatio", Params.nonNegative("tauMinRatio", b.tauMinRatio), 1);  // [0, 1)
        explorers = Params.between("explorers", b.explorers, 0, antCount);
        explorerRate = Params.probability("explorerRate", b.explorerRate);
        explorerBeta = Params.nonNegative("explorerBeta", b.explorerBeta);
        explorerDeposit = Params.nonNegative("explorerDeposit", b.explorerDeposit);
    }

    public static Builder builder() {
        return new Builder();
    }

    private record Scored(PECTOSolution solution, double cost) {
    }

    @Override
    public void search(PECTOProblem problem, SearchContext<PECTOSolution> ctx) {
        PECTOSolution greedy = GreedyPECTOSG.complete(problem.emptySolution());
        if (ctx.shouldStop())
            return;
        Scored best = new Scored(greedy, ctx.evaluate(greedy));
        if (problem.patientCount() == 0)
            return;

        CandidateLists lists = new CandidateLists(problem, candidates);
        Pheromone pheromone = new Pheromone(this, problem, lists);
        pheromone.bound(best.cost());
        pheromone.fill();
        List<Ant> ants = ants(problem, lists, pheromone, ctx.rng());
        try (AntBuilds builds = AntBuilds.of(executionMode, threads, ants, problem)) {
            iterate(ctx, pheromone, builds, best);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // cancels the run: ctx.shouldStop() sees the interrupt
        }
    }

    // every ant with its own RNG, derived from the run's; the last `explorers` ants sometimes ignore the pheromone
    private List<Ant> ants(PECTOProblem problem, CandidateLists lists, Pheromone pheromone, RNG rng) {
        long antSeed = Seeds.derive(rng.randInt(Integer.MAX_VALUE), rng.randInt(Integer.MAX_VALUE));
        List<Ant> ants = new ArrayList<>(antCount);
        for (int ant = 0; ant < antCount; ant++)
            ants.add(new Ant(this, problem, lists, pheromone, new MersenneTwisterRNG(Seeds.derive(antSeed, ant)),
                    isExplorer(ant) ? explorerRate : 0));
        return ants;
    }

    private boolean isExplorer(int ant) {
        return ant >= antCount - explorers;
    }

    private void iterate(SearchContext<PECTOSolution> ctx, Pheromone pheromone, AntBuilds builds, Scored best)
            throws InterruptedException {
        RNG rng = ctx.rng();
        int explorerWins = 0;
        for (int iteration = 0; !ctx.shouldStop(); iteration++) {
            pheromone.weigh();
            Iteration built = buildAndEvaluate(ctx, builds);
            if (built == null)
                return;
            Scored iterationBest = improve(built.best, ctx, rng);
            if (iterationBest == null)
                return;
            if (built.explorerWon)
                explorerWins++;
            if (iterationBest.cost() < best.cost()) {
                best = iterationBest;
                pheromone.bound(best.cost());
            }
            updatePheromone(pheromone, iteration, iterationBest, best, built.explorerBest);
            ctx.iterationCompleted(metrics(built, iterationBest, pheromone, explorerWins), built.solutions);
        }
    }

    // the ants of the iteration, built and evaluated in ant order; null if the run stopped
    private Iteration buildAndEvaluate(SearchContext<PECTOSolution> ctx, AntBuilds builds) throws InterruptedException {
        builds.startIteration();
        Iteration built = new Iteration(antCount);
        for (int ant = 0; ant < antCount; ant++) {
            if (ctx.shouldStop())
                return null;
            PECTOSolution solution = builds.solution(ant);
            built.add(solution, ctx.evaluate(solution), isExplorer(ant));
        }
        return built;
    }

    // the ants' solutions of an iteration: the best (ties: the first), the best explorer and the mean cost
    private static final class Iteration {
        final List<PECTOSolution> solutions;
        Scored best, explorerBest;
        boolean explorerWon;            // the best is an explorer
        private double costSum;

        Iteration(int ants) {
            solutions = new ArrayList<>(ants);
        }

        void add(PECTOSolution solution, double cost, boolean explorer) {
            solutions.add(solution);
            costSum += cost;
            if (best == null || cost < best.cost()) {
                best = new Scored(solution, cost);
                explorerWon = explorer;
            }
            if (explorer && (explorerBest == null || cost < explorerBest.cost()))
                explorerBest = new Scored(solution, cost);
        }

        double meanCost() {
            return costSum / solutions.size();
        }
    }

    // the solution after `localSearchSteps` random MoveTrip / SwapTrips neighbors (first improvement), then the optimal
    // hospitals for the routes it ends with; null if the run stopped
    private Scored improve(Scored start, SearchContext<PECTOSolution> ctx, RNG rng) {
        PECTOSolution solution = start.solution();
        double solutionCost = start.cost();
        for (int step = 0; step < localSearchSteps; step++) {
            if (ctx.shouldStop())
                return null;
            PECTOSolution neighbor = MOVES.get(rng.randInt(MOVES.size())).apply(solution, rng);
            double cost = ctx.evaluate(neighbor);
            if (cost < solutionCost) {
                solution = neighbor;
                solutionCost = cost;
            }
        }
        if (optimalHospitals) {
            PECTOSolution reassigned = OptimalHospitals.of(solution);
            if (reassigned != solution) {
                if (ctx.shouldStop())
                    return null;
                double cost = ctx.evaluate(reassigned);
                if (cost <= solutionCost) {
                    solution = reassigned;
                    solutionCost = cost;
                }
            }
        }
        return solution == start.solution() ? start : new Scored(solution, solutionCost);
    }

    // The iteration best deposits 1 / cost, every GLOBAL_BEST_PERIOD-th iteration the best so far does instead. The
    // best explorer adds explorerDeposit / cost, unless it is the solution that deposits anyway.
    private void updatePheromone(Pheromone pheromone, int iteration, Scored iterationBest, Scored best,
                                 Scored explorerBest) {
        boolean global = iteration % GLOBAL_BEST_PERIOD == GLOBAL_BEST_PERIOD - 1;
        Scored deposits = global ? best : iterationBest;
        pheromone.evaporate();
        pheromone.deposit(deposits.solution(), 1 / Math.max(deposits.cost(), MIN_COST));
        if (explorerDeposit > 0 && explorerBest != null && explorerBest.solution() != deposits.solution())
            pheromone.deposit(explorerBest.solution(), explorerDeposit / Math.max(explorerBest.cost(), MIN_COST));
        pheromone.clamp();
    }

    private Map<String, Double> metrics(Iteration built, Scored iterationBest, Pheromone pheromone, int explorerWins) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put(Metrics.MEAN_COST, built.meanCost());
        metrics.put(Metrics.CURRENT_COST, iterationBest.cost());
        metrics.put(BRANCHING_FACTOR, pheromone.branchingFactor());
        if (explorers > 0)
            metrics.put(EXPLORER_WINS, (double) explorerWins);
        return metrics;
    }

    @Override
    protected String parameters() {
        return "ants=" + antCount + ", alpha=" + alpha + ", beta=" + beta + ", rho=" + rho + ", pBest=" + pBest
                + ", candidates=" + candidates + ", hospitalCandidates=" + hospitalCandidates + ", shortlist=" + shortlist
                + ", localSearchSteps=" + localSearchSteps
                + ", optimalHospitals=" + optimalHospitals + ", executionMode=" + executionMode
                + (executionMode == ExecutionMode.SYNCHRONOUS ? ", threads=" + threads : "")
                + (tauMinRatio > 0 ? ", tauMinRatio=" + tauMinRatio : "")
                + ", explorers=" + explorers
                + (explorers > 0 ? ", explorerRate=" + explorerRate + ", explorerBeta=" + explorerBeta
                        + ", explorerDeposit=" + explorerDeposit : "");
    }

    public static final class Builder {
        private String name = "PECTOACO";
        private int antCount = 20;
        private double alpha = 1;
        private double beta = 2;
        private double rho = 0.1;
        private double pBest = 0.05;
        private int candidates = 20;
        private int hospitalCandidates = 5;
        private int shortlist = 3;
        private int localSearchSteps = 100;
        private boolean optimalHospitals = true;
        private ExecutionMode executionMode = ExecutionMode.SEQUENTIAL;
        private int threads = Runtime.getRuntime().availableProcessors();
        private double tauMinRatio = 0;
        private int explorers = 2;
        private double explorerRate = 0.05;
        private double explorerBeta = 0.5;
        private double explorerDeposit = 0.05;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /** Ants per iteration; default 20. */
        public Builder antCount(int antCount) {
            this.antCount = antCount;
            return this;
        }

        /** Pheromone influence; default 1. */
        public Builder alpha(double alpha) {
            this.alpha = alpha;
            return this;
        }

        /** Heuristic influence; default 2. */
        public Builder beta(double beta) {
            this.beta = beta;
            return this;
        }

        /** Evaporation rate in (0, 1]; default 0.1. */
        public Builder rho(double rho) {
            this.rho = rho;
            return this;
        }

        /**
         * Probability of building the best solution once the pheromone has converged, in (0, 1); sets tau_min;
         * default 0.05.
         */
        public Builder pBest(double pBest) {
            this.pBest = pBest;
            return this;
        }

        /** Nearest patients considered after each stop; default 20. */
        public Builder candidates(int candidates) {
            this.candidates = candidates;
            return this;
        }

        /** Nearest hospitals with a free bed among which a patient's hospital is drawn; default 5. */
        public Builder hospitalCandidates(int hospitalCandidates) {
            this.hospitalCandidates = hospitalCandidates;
            return this;
        }

        /** Best rated ambulances among which the moving one is drawn; 1 = always the best; default 3. */
        public Builder shortlist(int shortlist) {
            this.shortlist = shortlist;
            return this;
        }

        /** Neighbors tried on the best ant of every iteration; 0 = no local search; default 100. */
        public Builder localSearchSteps(int localSearchSteps) {
            this.localSearchSteps = localSearchSteps;
            return this;
        }

        /** Whether the best ant of every iteration gets the optimal hospitals for its routes; default true. */
        public Builder optimalHospitals(boolean optimalHospitals) {
            this.optimalHospitals = optimalHospitals;
            return this;
        }

        /**
         * SEQUENTIAL or SYNCHRONOUS (ants built in parallel); both give the same run for the same seed; default
         * SEQUENTIAL.
         */
        public Builder executionMode(ExecutionMode executionMode) {
            this.executionMode = executionMode;
            return this;
        }

        /** Threads building the ants in SYNCHRONOUS mode, at most antCount are used; default: available processors. */
        public Builder threads(int threads) {
            this.threads = threads;
            return this;
        }

        /**
         * Floor of tau_min as a fraction of tau_max, in [0, 1); the pBest formula gives a tiny tau_min on large
         * instances, so the ants stop trying other choices. E.g. 1 / (2 candidates) = 0.025; default 0 (pBest only).
         */
        public Builder tauMinRatio(double tauMinRatio) {
            this.tauMinRatio = tauMinRatio;
            return this;
        }

        /**
         * Ants of every iteration that sometimes ignore the pheromone (the last ones), at most antCount; 0 = the plain
         * colony; default 2.
         */
        public Builder explorers(int explorers) {
            this.explorers = explorers;
            return this;
        }

        /**
         * The probability that an explorer's patient or hospital draw ignores the pheromone, in [0, 1]; default 0.05.
         * Large rates make explorers far worse than the colony's ants, so that they no longer help.
         */
        public Builder explorerRate(double explorerRate) {
            this.explorerRate = explorerRate;
            return this;
        }

        /**
         * Heuristic exponent of the draws that ignore the pheromone; lower than beta so that they spread over the
         * candidates instead of taking the nearest (0: uniform over the candidates); default 0.5.
         */
        public Builder explorerBeta(double explorerBeta) {
            this.explorerBeta = explorerBeta;
            return this;
        }

        /**
         * The best explorer of every iteration deposits explorerDeposit / cost, unless it deposits as the best;
         * 0 = no deposit, 1 = as much as the best solution; default 0.05.
         */
        public Builder explorerDeposit(double explorerDeposit) {
            this.explorerDeposit = explorerDeposit;
            return this;
        }

        public PECTOACO build() {
            return new PECTOACO(this);
        }
    }
}
