package problems.pecto.aco;

import base.MutationOperator;
import metaheuristic.AbstractMetaheuristic;
import metaheuristic.Params;
import metaheuristic.run.Metrics;
import metaheuristic.run.SearchContext;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Route;
import problems.pecto.operators.GreedyPECTOSG;
import problems.pecto.operators.MoveTrip;
import problems.pecto.operators.OptimalHospitals;
import problems.pecto.operators.SwapTrips;
import util.random.MersenneTwisterRNG;
import util.random.RNG;
import util.random.Seeds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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
 * <b>Execution.</b> {@link ExecutionMode#SEQUENTIAL} builds the ants one after the other on the run's thread;
 * {@link ExecutionMode#SYNCHRONOUS} builds them in parallel on {@code threads} threads, against the pheromone of the
 * iteration, which no ant changes. Everything else (evaluations, local search, pheromone update) stays on the run's
 * thread, in ant order. Every ant has its own RNG, derived from the run's, so both modes give the same run for the
 * same seed, whatever the number of threads.
 * <p>
 * <b>Explorer ants</b>, against early convergence. The last {@code explorers} ants of every iteration build like the
 * others, except that each of their patient and hospital draws ignores the pheromone with probability
 * {@code explorerRate}: every candidate then has pheromone weight 1 and the heuristic exponent is {@code explorerBeta}.
 * Their solutions stay close to what the colony learned, so they can be good, but differ from it. The best explorer
 * of the iteration deposits {@code explorerDeposit} / cost on its choices, unless it is the solution that deposits
 * anyway; this keeps the pheromone from collapsing onto one solution. On the large Table I instances, 2 explorers with
 * rate 0.05 and deposit 0.05 (the defaults) gave about 0.2% lower costs than the plain colony. Solutions built
 * without any pheromone (rate 1) are far worse than the colony's and their deposit does not help. {@code tauMinRatio}
 * raises tau_min, so that no choice becomes practically impossible (off by default).
 */
public final class PECTOACO extends AbstractMetaheuristic<PECTOSolution, PECTOProblem> {

    static final int GLOBAL_BEST_PERIOD = 5;
    // the lambda-branching factor of the patient pheromone (Gambardella & Dorigo): the mean number of candidates per
    // node whose pheromone is at least tau_min + LAMBDA (tau_max - tau_min); about `candidates` at the start, 1 when
    // the colony has converged to one successor per node
    public static final String BRANCHING_FACTOR = "branchingFactor";
    // the number of iterations so far whose best ant was an explorer
    public static final String EXPLORER_WINS = "explorerWins";
    private static final double LAMBDA = 0.05;
    private static final byte EXACT = 0, BOUND = 1, UNKNOWN = 2;
    private static final double MIN_COST = 1e-10;
    private static final List<MutationOperator<PECTOSolution>> MOVES = Arrays.asList(new MoveTrip(), new SwapTrips());

    private final int antCount;
    private final double alpha;
    private final double beta;
    private final double rho;
    private final double pBest;
    private final int candidates;
    private final int hospitalCandidates;
    private final int shortlist;
    private final int localSearchSteps;
    private final boolean optimalHospitals;
    private final ExecutionMode executionMode;
    private final int threads;
    private final double tauMinRatio;
    private final int explorers;
    private final double explorerRate;
    private final double explorerBeta;
    private final double explorerDeposit;

    public enum ExecutionMode { SEQUENTIAL, SYNCHRONOUS }

    private PECTOACO(Builder b) {
        super(b.name);
        this.antCount = Params.positive("antCount", b.antCount);
        this.alpha = Params.nonNegative("alpha", b.alpha);
        this.beta = Params.nonNegative("beta", b.beta);
        this.rho = Params.positive("rho", Params.probability("rho", b.rho));
        this.pBest = Params.positive("pBest", Params.probability("pBest", b.pBest));
        if (pBest == 1)
            throw new IllegalArgumentException("pBest must be below 1");
        this.candidates = Params.positive("candidates", b.candidates);
        this.hospitalCandidates = Params.positive("hospitalCandidates", b.hospitalCandidates);
        this.shortlist = Params.positive("shortlist", b.shortlist);
        if (b.localSearchSteps < 0)
            throw new IllegalArgumentException("localSearchSteps must not be negative, was " + b.localSearchSteps);
        this.localSearchSteps = b.localSearchSteps;
        this.optimalHospitals = b.optimalHospitals;
        if (b.executionMode == null)
            throw new IllegalArgumentException("executionMode must not be null");
        this.executionMode = b.executionMode;
        this.threads = Params.positive("threads", b.threads);
        this.tauMinRatio = Params.probability("tauMinRatio", b.tauMinRatio);
        if (tauMinRatio == 1)
            throw new IllegalArgumentException("tauMinRatio must be below 1");
        if (b.explorers < 0 || b.explorers > antCount)
            throw new IllegalArgumentException("explorers must be between 0 and antCount, was " + b.explorers);
        this.explorers = b.explorers;
        this.explorerRate = Params.probability("explorerRate", b.explorerRate);
        this.explorerBeta = Params.nonNegative("explorerBeta", b.explorerBeta);
        this.explorerDeposit = Params.nonNegative("explorerDeposit", b.explorerDeposit);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void search(PECTOProblem problem, SearchContext<PECTOSolution> ctx) {
        RNG rng = ctx.rng();
        PECTOSolution best = GreedyPECTOSG.complete(problem.emptySolution());
        if (ctx.shouldStop())
            return;
        double bestCost = ctx.evaluate(best);
        if (problem.patientCount() == 0)
            return;

        Colony colony = new Colony(problem);
        colony.bound(bestCost);
        colony.fill();
        long antSeed = Seeds.derive(rng.randInt(Integer.MAX_VALUE), rng.randInt(Integer.MAX_VALUE));
        List<Callable<PECTOSolution>> ants = new ArrayList<>(antCount);
        for (int ant = 0; ant < antCount; ant++) {
            // the last `explorers` ants sometimes ignore the pheromone
            Colony.Ant a = colony.new Ant(new MersenneTwisterRNG(Seeds.derive(antSeed, ant)),
                    ant >= antCount - explorers ? explorerRate : 0);
            // the cost is computed (and cached in the routes) by the ant, so that ctx.evaluate only adds it up
            ants.add(() -> {
                PECTOSolution solution = a.construct();
                problem.cost(solution);
                return solution;
            });
        }
        ExecutorService pool = executionMode == ExecutionMode.SYNCHRONOUS ? newPool(Math.min(threads, antCount)) : null;
        try {
            search(problem, ctx, colony, ants, pool, best, bestCost);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // cancels the run: ctx.shouldStop() sees the interrupt
        } finally {
            if (pool != null)
                pool.shutdownNow();
        }
    }

    private void search(PECTOProblem problem, SearchContext<PECTOSolution> ctx, Colony colony,
                        List<Callable<PECTOSolution>> ants, ExecutorService pool, PECTOSolution best, double bestCost)
            throws InterruptedException {
        RNG rng = ctx.rng();
        int explorerWins = 0;
        for (int iteration = 0; !ctx.shouldStop(); iteration++) {
            colony.weigh();
            List<Future<PECTOSolution>> built = pool == null ? null : pool.invokeAll(ants);
            List<PECTOSolution> solutions = new ArrayList<>(antCount);
            Scored iterationBest = null, explorerBest = null;
            boolean explorerWon = false;
            double sum = 0;
            for (int ant = 0; ant < antCount; ant++) {
                if (ctx.shouldStop())
                    return;
                PECTOSolution solution = built == null ? call(ants.get(ant)) : result(built.get(ant));
                solutions.add(solution);
                double cost = ctx.evaluate(solution);
                sum += cost;
                boolean explorer = ant >= antCount - explorers;
                if (iterationBest == null || cost < iterationBest.cost) {
                    iterationBest = new Scored(solution, cost);
                    explorerWon = explorer;
                }
                if (explorer && (explorerBest == null || cost < explorerBest.cost))
                    explorerBest = new Scored(solution, cost);
            }

            iterationBest = improve(iterationBest, localSearchSteps, ctx, rng);
            if (iterationBest == null)
                return;
            if (explorerWon)
                explorerWins++;
            double iterationBestCost = iterationBest.cost;

            if (iterationBestCost < bestCost) {
                best = iterationBest.solution;
                bestCost = iterationBestCost;
                colony.bound(bestCost);
            }
            boolean global = iteration % GLOBAL_BEST_PERIOD == GLOBAL_BEST_PERIOD - 1;
            PECTOSolution deposits = global ? best : iterationBest.solution;
            colony.update(deposits, 1 / Math.max(global ? bestCost : iterationBestCost, MIN_COST),
                    explorerDeposit > 0 && explorerBest != null && explorerBest.solution != deposits
                            ? explorerBest.solution : null,
                    explorerDeposit / Math.max(explorerBest == null ? 1 : explorerBest.cost, MIN_COST));

            Map<String, Double> metrics = new LinkedHashMap<>();
            metrics.put(Metrics.MEAN_COST, sum / antCount);
            metrics.put(Metrics.CURRENT_COST, iterationBestCost);
            metrics.put(BRANCHING_FACTOR, colony.branchingFactor());
            if (explorers > 0)
                metrics.put(EXPLORER_WINS, (double) explorerWins);
            ctx.iterationCompleted(metrics, solutions);
        }
    }

    private record Scored(PECTOSolution solution, double cost) {
    }

    // the solution after `steps` random MoveTrip / SwapTrips neighbors (first improvement), then the optimal hospitals
    // for the routes it ends with; null if the run stopped
    private Scored improve(Scored start, int steps, SearchContext<PECTOSolution> ctx, RNG rng) {
        PECTOSolution solution = start.solution;
        double solutionCost = start.cost;
        for (int step = 0; step < steps; step++) {
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
        return solution == start.solution ? start : new Scored(solution, solutionCost);
    }

    private static PECTOSolution call(Callable<PECTOSolution> ant) {
        try {
            return ant.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e); // ants throw no checked exceptions
        }
    }

    // the solution of an ant built by the pool, rethrowing what the ant threw
    private static PECTOSolution result(Future<PECTOSolution> future) throws InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException r)
                throw r;
            if (e.getCause() instanceof Error err)
                throw err;
            throw new IllegalStateException(e.getCause());
        }
    }

    // daemon threads, so that a pool can never keep the JVM alive
    private static ExecutorService newPool(int threads) {
        AtomicInteger count = new AtomicInteger();
        return Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "PECTOACO-ant-" + count.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * The pheromone of a run and the data its ants share; both are only read while the ants build. Patients and
     * hospitals are participant IDs except where an array is indexed by patient or hospital index (problem.index).
     */
    private final class Colony {

        private final PECTOProblem problem;
        private final int ambulances, patients, hospitals;

        // candidates[n]: the nearest patients of node n (an ambulance or a patient ID), by urgency-weighted distance
        private final int[][] candidates;
        private final double[][] tauPatient;        // tau(prev, q), aligned with candidates
        private final double[][] tauHospital;       // tau(q, h) by patient and hospital index
        private final double[][] weightPatient;     // tau^alpha, recomputed every iteration
        private final double[][] weightHospital;
        private final int[][] hospitalOrder;        // hospital indices by distance from the patient, by patient index
        private final int[][] candidateOf;          // the nodes whose candidates include the patient, by patient index
        // byDistance[l]: all patients by urgency-weighted distance from location l, where an ambulance can be: its
        // start (l = its ID) or a hospital (l = A + index)
        private final int[][] byDistance;
        private double tauMax, tauMinPatient, tauMinHospital, weightMinPatient;

        Colony(PECTOProblem problem) {
            this.problem = problem;
            ambulances = problem.ambulanceCount();
            patients = problem.patientCount();
            hospitals = problem.hospitalCount();

            int nodes = ambulances + patients;
            candidates = new int[nodes][];
            tauPatient = new double[nodes][];
            weightPatient = new double[nodes][];
            for (int n = 0; n < nodes; n++) {
                candidates[n] = nearestPatients(n);
                tauPatient[n] = new double[candidates[n].length];
                weightPatient[n] = new double[candidates[n].length];
            }
            tauHospital = new double[patients][hospitals];
            weightHospital = new double[patients][hospitals];
            hospitalOrder = new int[patients][];
            int[] counts = new int[patients];
            for (int[] c : candidates)
                for (int q : c)
                    counts[problem.index(q)]++;
            candidateOf = new int[patients][];
            for (int k = 0; k < patients; k++)
                candidateOf[k] = new int[counts[k]];
            Arrays.fill(counts, 0);
            for (int n = 0; n < nodes; n++)
                for (int q : candidates[n])
                    candidateOf[problem.index(q)][counts[problem.index(q)]++] = n;
            byDistance = new int[ambulances + hospitals][];
            for (int l = 0; l < byDistance.length; l++) {
                int location = l < ambulances ? l : problem.hospitalId(l - ambulances);
                byDistance[l] = IntStream.range(0, patients).map(problem::patientId).boxed()
                        .sorted(Comparator.comparingDouble(q -> problem.urgencyWeight(q) * problem.distance(location, q)))
                        .mapToInt(Integer::intValue).toArray();
            }
            for (int k = 0; k < patients; k++) {
                int patient = problem.patientId(k);
                hospitalOrder[k] = IntStream.range(0, hospitals).boxed()
                        .sorted(Comparator.comparingDouble(h -> problem.distance(patient, problem.hospitalId(h))))
                        .mapToInt(Integer::intValue).toArray();
            }
        }

        // the PECTOACO.this.candidates nearest patients of the node, nearest first
        private int[] nearestPatients(int node) {
            int size = Math.min(PECTOACO.this.candidates, problem.isPatient(node) ? patients - 1 : patients);
            int[] nearest = new int[size];
            double[] distance = new double[size];
            int count = 0;
            for (int k = 0; k < patients; k++) {
                int q = problem.patientId(k);
                if (q == node)
                    continue;
                double d = problem.urgencyWeight(q) * problem.distance(node, q);
                if (count == size && d >= distance[size - 1])
                    continue;
                int i = count < size ? count++ : size - 1;
                while (i > 0 && distance[i - 1] > d) {
                    nearest[i] = nearest[i - 1];
                    distance[i] = distance[i - 1];
                    i--;
                }
                nearest[i] = q;
                distance[i] = d;
            }
            return nearest;
        }

        // ---- pheromone

        // the bounds for the best cost so far
        void bound(double bestCost) {
            tauMax = 1 / (rho * Math.max(bestCost, MIN_COST));
            tauMinPatient = tauMin(PECTOACO.this.candidates);
            tauMinHospital = tauMin(Math.min(hospitalCandidates, hospitals));
        }

        // Stützle & Hoos: the best solution is built with probability pBest once the pheromone has converged; with
        // many patients this is a tiny fraction of tau_max, so tauMinRatio can set a floor
        private double tauMin(int choices) {
            double root = Math.pow(pBest, 1.0 / patients);
            double average = Math.max(2, choices / 2.0);
            double tauMin = Math.max(tauMax * (1 - root) / ((average - 1) * root), tauMinRatio * tauMax);
            return Math.min(tauMax, tauMin);
        }

        // every pheromone value at tau_max
        void fill() {
            for (double[] row : tauPatient)
                Arrays.fill(row, tauMax);
            for (double[] row : tauHospital)
                Arrays.fill(row, tauMax);
        }

        // tau^alpha for the ants of this iteration
        void weigh() {
            for (int n = 0; n < tauPatient.length; n++)
                for (int j = 0; j < tauPatient[n].length; j++)
                    weightPatient[n][j] = Math.pow(tauPatient[n][j], alpha);
            for (int q = 0; q < patients; q++)
                for (int h = 0; h < hospitals; h++)
                    weightHospital[q][h] = Math.pow(tauHospital[q][h], alpha);
            weightMinPatient = Math.pow(tauMinPatient, alpha);
        }

        // evaporates, deposits the amounts on the choices that build the solutions (explorer may be null) and keeps
        // the bounds
        void update(PECTOSolution solution, double amount, PECTOSolution explorer, double explorerAmount) {
            for (double[] row : tauPatient)
                for (int j = 0; j < row.length; j++)
                    row[j] *= 1 - rho;
            for (double[] row : tauHospital)
                for (int h = 0; h < row.length; h++)
                    row[h] *= 1 - rho;
            deposit(solution, amount);
            if (explorer != null)
                deposit(explorer, explorerAmount);
            for (double[] row : tauPatient)
                for (int j = 0; j < row.length; j++)
                    row[j] = Math.min(tauMax, Math.max(tauMinPatient, row[j]));
            for (double[] row : tauHospital)
                for (int h = 0; h < row.length; h++)
                    row[h] = Math.min(tauMax, Math.max(tauMinHospital, row[h]));
        }

        private void deposit(PECTOSolution solution, double amount) {
            for (Route route : solution.routes()) {
                int prev = route.ambulance();
                for (int t = 0; t < route.tripCount(); t++) {
                    int q = route.patient(t);
                    int j = indexOf(candidates[prev], q);
                    if (j >= 0)
                        tauPatient[prev][j] += amount;
                    tauHospital[problem.index(q)][problem.index(route.hospital(t))] += amount;
                    prev = q;
                }
            }
        }

        double branchingFactor() {
            double threshold = tauMinPatient + LAMBDA * (tauMax - tauMinPatient);
            int nodes = 0, branches = 0;
            for (double[] row : tauPatient) {
                if (row.length == 0)
                    continue;
                nodes++;
                for (double tau : row)
                    if (tau >= threshold)
                        branches++;
            }
            return nodes == 0 ? 0 : (double) branches / nodes;
        }

        private int indexOf(int[] values, int value) {
            for (int j = 0; j < values.length; j++)
                if (values[j] == value)
                    return j;
            return -1;
        }

        // ---- construction

        // An ant: the state of the solution it is building and its own RNG. Used by one thread at a time.
        final class Ant {

            private final RNG rng;
            // the probability that a patient or hospital draw ignores the pheromone: every choice then has pheromone
            // weight 1 (ones) and the heuristic exponent is explorerBeta. 0 for the colony's ants, whose ones is null
            private final double explore;
            private final double[] ones;
            private final boolean[] served;             // by patient index
            private final int[] room;                   // free beds, by hospital index
            private final int[] previous;               // the ambulance or its last patient
            private final int[] at;                     // where the ambulance is: its position or a hospital
            private final int[] pending;                // its last patient, whose hospital is provisional; -1 if none
            private final int[] pendingHospital;
            private final int[][] stops;                // the trips fixed so far, by ambulance
            private final int[] stopCount;
            private final int[] remaining;              // the unserved candidates of every node
            private final int[] nearestUnserved;        // skips the served patients at the front of byDistance[l]
            // An ambulance is rated by its most attractive next patient. While it stays where it is, serving patients
            // can only lower its rating, so a stale rating is an upper bound and is recomputed only if it could make
            // the shortlist. It is not a bound when the last candidate is served: the nearest unserved patient may be
            // nearer.
            private final double[] rating;
            private final int[] rated;                  // the patient the rating is for
            private final byte[] rateState;             // EXACT, BOUND or UNKNOWN
            private final boolean[] ratedNearest;       // rated by the nearest unserved patient, no candidate being left
            private final int[] top;
            private final double[] choiceWeights;
            private final int[] choiceHospitals;

            Ant(RNG rng, double explore) {
                this.rng = rng;
                this.explore = explore;
                if (explore > 0) {
                    ones = new double[Math.max(PECTOACO.this.candidates, hospitals)];
                    Arrays.fill(ones, 1);
                } else
                    ones = null;
                served = new boolean[patients];
                room = new int[hospitals];
                previous = new int[ambulances];
                at = new int[ambulances];
                pending = new int[ambulances];
                pendingHospital = new int[ambulances];
                stops = new int[ambulances][16];
                stopCount = new int[ambulances];
                remaining = new int[ambulances + patients];
                nearestUnserved = new int[byDistance.length];
                rating = new double[ambulances];
                rated = new int[ambulances];
                rateState = new byte[ambulances];
                ratedNearest = new boolean[ambulances];
                top = new int[Math.min(shortlist, ambulances)];
                choiceHospitals = new int[Math.min(hospitalCandidates, hospitals)];
                choiceWeights = new double[Math.max(choiceHospitals.length, PECTOACO.this.candidates)];
            }

            PECTOSolution construct() {
                Arrays.fill(served, false);
                for (int h = 0; h < hospitals; h++)
                    room[h] = problem.capacity(problem.hospitalId(h));
                for (int a = 0; a < ambulances; a++) {
                    previous[a] = a;
                    at[a] = a;
                    pending[a] = -1;
                    stopCount[a] = 0;
                    rateState[a] = UNKNOWN;
                }
                for (int n = 0; n < remaining.length; n++)
                    remaining[n] = candidates[n].length;
                Arrays.fill(nearestUnserved, 0);

                for (int step = 0; step < patients; step++) {
                    int a = chooseAmbulance();
                    pickUp(a, choosePatient(a));
                }

                int[][] result = new int[ambulances][];
                for (int a = 0; a < ambulances; a++) {
                    if (pending[a] >= 0)
                        append(a, pending[a], pendingHospital[a]);
                    result[a] = Arrays.copyOf(stops[a], stopCount[a]);
                }
                return problem.solution(result);
            }

            // drawn uniformly from the shortlist of best rated ambulances (ties: lower ID first)
            private int chooseAmbulance() {
                int count = 0;
                for (int a = 0; a < ambulances; a++) {
                    if (rateState[a] == UNKNOWN)
                        rate(a);
                    if (count == top.length && rating[a] <= rating[top[count - 1]])
                        continue;       // an upper bound is enough to rule it out
                    if (rateState[a] == BOUND) {
                        rate(a);
                        if (count == top.length && rating[a] <= rating[top[count - 1]])
                            continue;
                    }
                    int i = count < top.length ? count++ : count - 1;
                    while (i > 0 && rating[top[i - 1]] < rating[a]) {
                        top[i] = top[i - 1];
                        i--;
                    }
                    top[i] = a;
                }
                return top[rng.randInt(count)];
            }

            // the most attractive unserved candidate of the ambulance, or the nearest unserved patient if none is left
            private void rate(int a) {
                int[] c = candidates[previous[a]];
                double[] w = weightPatient[previous[a]];
                double best = -1;
                int bestPatient = -1;
                for (int j = 0; j < c.length && remaining[previous[a]] > 0; j++)
                    if (!served[problem.index(c[j])]) {
                        double score = w[j] * eta(problem.urgencyWeight(c[j]) * problem.distance(at[a], c[j]), beta);
                        if (score > best) {
                            best = score;
                            bestPatient = c[j];
                        }
                    }
                if (bestPatient < 0) {
                    // all candidates served: the nearest unserved patient, at tau_min
                    int l = problem.isAmbulance(at[a]) ? at[a] : ambulances + problem.index(at[a]);
                    int[] order = byDistance[l];
                    while (served[problem.index(order[nearestUnserved[l]])])
                        nearestUnserved[l]++;
                    bestPatient = order[nearestUnserved[l]];
                    best = weightMinPatient * eta(problem.urgencyWeight(bestPatient) * problem.distance(at[a], bestPatient), beta);
                }
                rating[a] = best;
                rated[a] = bestPatient;
                rateState[a] = EXACT;
                ratedNearest[a] = remaining[previous[a]] == 0;
            }

            private int choosePatient(int a) {
                int[] c = candidates[previous[a]];
                boolean free = explores();
                double[] w = free ? ones : weightPatient[previous[a]];
                double b = free ? explorerBeta : beta;
                double sum = 0;
                for (int j = 0; j < c.length; j++) {
                    choiceWeights[j] = served[problem.index(c[j])] ? 0
                            : w[j] * eta(problem.urgencyWeight(c[j]) * problem.distance(at[a], c[j]), b);
                    sum += choiceWeights[j];
                }
                if (!(sum > 0))
                    return rated[a];        // no candidate left: the nearest unserved patient
                int j = roulette(c.length, sum);
                return served[problem.index(c[j])] ? rated[a] : c[j];
            }

            // the ambulance takes patient q: its pending patient gets its final hospital, q a provisional one
            private void pickUp(int a, int q) {
                int p = pending[a];
                if (p >= 0) {
                    room[problem.index(pendingHospital[a])]++;
                    int h = chooseHospital(p, q);
                    room[problem.index(h)]--;
                    append(a, p, h);
                }
                int h = chooseHospital(q, -1);
                room[problem.index(h)]--;
                pending[a] = q;
                pendingHospital[a] = h;
                previous[a] = q;
                at[a] = h;

                int k = problem.index(q);
                served[k] = true;
                for (int n : candidateOf[k])
                    remaining[n]--;
                for (int b = 0; b < ambulances; b++)
                    if (rated[b] == q && rateState[b] == EXACT)
                        rateState[b] = ratedNearest[b] || remaining[previous[b]] > 0 ? BOUND : UNKNOWN;
                rateState[a] = UNKNOWN;
            }

            // one of the nearest hospitals with a free bed for patient p, followed by patient next (-1 if none)
            private int chooseHospital(int p, int next) {
                int k = problem.index(p);
                boolean free = explores();
                double[] w = free ? ones : weightHospital[k];
                double b = free ? explorerBeta : beta;
                int count = 0;
                double sum = 0;
                for (int i = 0; i < hospitals && count < choiceHospitals.length; i++) {
                    int h = hospitalOrder[k][i];
                    if (room[h] == 0)
                        continue;
                    int hospital = problem.hospitalId(h);
                    double cost = problem.urgencyWeight(p) * problem.distance(p, hospital)
                            + (next < 0 ? 0 : problem.urgencyWeight(next) * problem.distance(hospital, next));
                    choiceHospitals[count] = h;
                    choiceWeights[count++] = w[h] * eta(cost, b);
                    sum += choiceWeights[count - 1];
                }
                // a bed is always left: the hospitals can take all patients, and every patient holds at most one
                return problem.hospitalId(choiceHospitals[sum > 0 ? roulette(count, sum) : 0]);
            }

            // an index below n drawn with probability proportional to choiceWeights, whose first n entries sum to sum
            private int roulette(int n, double sum) {
                double value = rng.randDouble() * sum;
                for (int i = 0; i < n; i++) {
                    value -= choiceWeights[i];
                    if (value < 0)
                        return i;
                }
                return n - 1;
            }

            // whether the next patient or hospital draw ignores the pheromone; only an explorer draws for it
            private boolean explores() {
                return explore > 0 && (explore >= 1 || rng.randDouble() < explore);
            }

            // (1 / cost)^b, b = beta (explorerBeta for draws that ignore the pheromone); Math.pow is avoided for the
            // usual exponents, as this is the innermost computation
            private double eta(double cost, double b) {
                double c = Math.max(cost, MIN_COST);
                if (b == 2)
                    return 1 / (c * c);
                if (b == 1)
                    return 1 / c;
                if (b == 0.5)
                    return 1 / Math.sqrt(c);
                if (b == 0)
                    return 1;
                return Math.pow(c, -b);
            }

            private void append(int a, int patient, int hospital) {
                if (stopCount[a] + 2 > stops[a].length)
                    stops[a] = Arrays.copyOf(stops[a], 2 * stops[a].length);
                stops[a][stopCount[a]++] = patient;
                stops[a][stopCount[a]++] = hospital;
            }
        }
    }

    @Override
    protected String parameters() {
        return "ants=" + antCount + ", alpha=" + alpha + ", beta=" + beta + ", rho=" + rho + ", pBest=" + pBest
                + ", candidates=" + candidates + ", hospitalCandidates=" + hospitalCandidates + ", shortlist=" + shortlist + ", localSearchSteps=" + localSearchSteps
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

        // heuristic influence; default 2
        public Builder beta(double beta) {
            this.beta = beta;
            return this;
        }

        // evaporation rate in (0, 1]; default 0.1
        public Builder rho(double rho) {
            this.rho = rho;
            return this;
        }

        // probability of building the best solution once the pheromone has converged, in (0, 1); sets tau_min; default 0.05
        public Builder pBest(double pBest) {
            this.pBest = pBest;
            return this;
        }

        // nearest patients considered after each stop; default 20
        public Builder candidates(int candidates) {
            this.candidates = candidates;
            return this;
        }

        // nearest hospitals with a free bed among which a patient's hospital is drawn; default 5
        public Builder hospitalCandidates(int hospitalCandidates) {
            this.hospitalCandidates = hospitalCandidates;
            return this;
        }

        // best rated ambulances among which the moving one is drawn; 1 = always the best; default 3
        public Builder shortlist(int shortlist) {
            this.shortlist = shortlist;
            return this;
        }

        // neighbors tried on the best ant of every iteration; 0 = no local search; default 100
        public Builder localSearchSteps(int localSearchSteps) {
            this.localSearchSteps = localSearchSteps;
            return this;
        }

        // whether the best ant of every iteration gets the optimal hospitals for its routes; default true
        public Builder optimalHospitals(boolean optimalHospitals) {
            this.optimalHospitals = optimalHospitals;
            return this;
        }

        // SEQUENTIAL or SYNCHRONOUS (ants built in parallel); both give the same run for the same seed; default SEQUENTIAL
        public Builder executionMode(ExecutionMode executionMode) {
            this.executionMode = executionMode;
            return this;
        }

        // threads building the ants in SYNCHRONOUS mode, at most antCount are used; default: available processors
        public Builder threads(int threads) {
            this.threads = threads;
            return this;
        }

        // floor of tau_min as a fraction of tau_max, in [0, 1); the pBest formula gives a tiny tau_min on large
        // instances, so the ants stop trying other choices. E.g. 1 / (2 candidates) = 0.025; default 0 (pBest only)
        public Builder tauMinRatio(double tauMinRatio) {
            this.tauMinRatio = tauMinRatio;
            return this;
        }

        // ants of every iteration that sometimes ignore the pheromone (the last ones), at most antCount; 0 = the plain
        // colony; default 2
        public Builder explorers(int explorers) {
            this.explorers = explorers;
            return this;
        }

        // in [0, 1]: the probability that an explorer's patient or hospital draw ignores the pheromone; default 0.05.
        // Large rates make explorers far worse than the colony's ants, so that they no longer help
        public Builder explorerRate(double explorerRate) {
            this.explorerRate = explorerRate;
            return this;
        }

        // heuristic exponent of the draws that ignore the pheromone; lower than beta so that they spread over the
        // candidates instead of taking the nearest (0: uniform over the candidates); default 0.5
        public Builder explorerBeta(double explorerBeta) {
            this.explorerBeta = explorerBeta;
            return this;
        }

        // the best explorer of every iteration deposits explorerDeposit / cost, unless it deposits as the best;
        // 0 = no deposit, 1 = as much as the best solution; default 0.05 (larger deposits helped less)
        public Builder explorerDeposit(double explorerDeposit) {
            this.explorerDeposit = explorerDeposit;
            return this;
        }

        public PECTOACO build() {
            return new PECTOACO(this);
        }
    }
}
