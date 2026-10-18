package core.algorithm.aco;

import core.algorithm.AbstractMetaheuristic;
import core.algorithm.Iterating;
import core.algorithm.localsearch.TerminalCondition;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.utils.random.RNG;
import core.utils.random.SplittableRNG;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ant Colony Optimization with pluggable (parallel) execution strategies.
 *
 * <h2>Execution modes</h2>
 * <ul>
 *   <li>{@link ExecutionMode#SEQUENTIAL}: classic ACO. In every iteration each ant builds a
 *       solution, then the trails are updated with all of them.</li>
 *   <li>{@link ExecutionMode#SYNCHRONOUS}: same algorithm, but ants build their solutions in
 *       parallel. The trails are not modified while ants construct, so no locking is needed,
 *       and with a seed the result is identical to {@code SEQUENTIAL}.</li>
 *   <li>{@link ExecutionMode#ASYNCHRONOUS}: every worker thread runs its share of the ants in a
 *       loop and updates the shared trails right after each solution, without iteration
 *       barriers. It has the highest throughput, but ants may read slightly stale pheromone and
 *       runs cannot be reproduced exactly.</li>
 *   <li>{@link ExecutionMode#ISLAND}: the colony is split into one sub-colony per thread, each
 *       with its own trails (see {@link PheromoneTrails#forColony(int)}). Every
 *       {@code migrationInterval} island iterations, each island reinforces its trails with
 *       the global best solution. This usually scales best because islands share almost
 *       nothing.</li>
 * </ul>
 *
 * <h2>Budget</h2>
 * One <em>iteration</em> is {@code colony.size()} constructed solutions in every mode, and the
 * terminal condition is evaluated exactly once per iteration (plus once before the first).
 * So {@code new IterationBasedTC(1000)} means 1000 &times; m solutions whatever the mode, and
 * sequential and parallel runs can be compared on equal work. {@link #iterationcount()}
 * exposes the same count for {@link core.algorithm.localsearch.IterationCountTC}.
 */
public class ACO extends AbstractMetaheuristic implements Iterating {

    private static final Logger LOG = Logger.getLogger(ACO.class.getName());

    public enum ExecutionMode { SEQUENTIAL, SYNCHRONOUS, ASYNCHRONOUS, ISLAND }

    private final PheromoneTrails pheromoneTrails;
    private final List<Ant> colony;
    private final TerminalCondition terminalCondition;
    private final ExecutionMode mode;

    private int threadCount = Runtime.getRuntime().availableProcessors();
    private Long seed;
    private int migrationInterval = 10;

    /** Solutions constructed in the current run, over all ants and threads. */
    private final AtomicLong constructedSolutions = new AtomicLong();
    /** Set once the terminal condition is met (or on failure) so that all workers stop. */
    private volatile boolean stopRequested;
    /** Seeded (or random) source from which every ant gets its own independent generator. */
    private RNG masterRng;

    public ACO(PheromoneTrails pheromoneTrails, List<Ant> colony, TerminalCondition terminalCondition) {
        this(pheromoneTrails, colony, terminalCondition, ExecutionMode.SEQUENTIAL);
    }

    public ACO(PheromoneTrails pheromoneTrails, List<Ant> colony, TerminalCondition terminalCondition, ExecutionMode mode) {
        super(null);
        if (colony.isEmpty())
            throw new IllegalArgumentException("The colony needs at least one ant");
        this.pheromoneTrails = pheromoneTrails;
        this.colony = List.copyOf(colony);
        this.terminalCondition = terminalCondition;
        this.mode = mode;
    }

    // ------------------------------------------------------------------ configuration

    /** Maximum number of worker threads for the parallel modes (default: available processors). */
    public ACO withThreads(int threadCount) {
        if (threadCount <= 0)
            throw new IllegalArgumentException("threadCount must be positive");
        this.threadCount = threadCount;
        return this;
    }

    /**
     * Makes every run start from the same random state. SEQUENTIAL and SYNCHRONOUS runs then
     * give identical results; the other modes still depend on thread scheduling.
     */
    public ACO withSeed(long seed) {
        this.seed = seed;
        return this;
    }

    /** Island mode: how many island iterations pass between migrations of the global best. */
    public ACO withMigrationInterval(int migrationInterval) {
        if (migrationInterval <= 0)
            throw new IllegalArgumentException("migrationInterval must be positive");
        this.migrationInterval = migrationInterval;
        return this;
    }

    public ExecutionMode getMode() {
        return mode;
    }

    /** @return number of solutions constructed so far in the current/last run */
    public long getSolutionCount() {
        return constructedSolutions.get();
    }

    /** @return colony-equivalent iterations performed so far (solutions / colony size) */
    @Override
    public long iterationcount() {
        return constructedSolutions.get() / colony.size();
    }

    @Override
    public String getName() {
        return "ACO[" + mode + ", m=" + colony.size() + ", rho=" + pheromoneTrails.getEvaporationRatio() + "]";
    }

    // ------------------------------------------------------------------ run

    @Override
    protected void init(OptimizationProblem problem) {
        super.init(problem);
        constructedSolutions.set(0);
        stopRequested = false;
        terminalCondition.init();
        masterRng = seed == null ? new SplittableRNG() : new SplittableRNG(seed);

        // Island mode builds per-island trails in runIslands(); every other mode shares one.
        if (mode != ExecutionMode.ISLAND) {
            pheromoneTrails.init(problem);
            for (Ant ant : colony) {
                ant.init(problem, pheromoneTrails, masterRng.split());
            }
        }
    }

    @Override
    protected void _perform(OptimizationProblem problem) {
        // The terminal condition is checked once before the first iteration and afterwards once
        // per completed colony iteration (see recordSolutions).
        stopRequested = isTerminated(problem);
        switch (mode) {
            case SEQUENTIAL -> runSequential(problem);
            case SYNCHRONOUS -> runSynchronous(problem);
            case ASYNCHRONOUS -> runAsynchronous(problem);
            case ISLAND -> runIslands(problem);
        }
        LOG.fine(() -> getName() + " finished after " + getSolutionCount() + " solutions, best = " + bestSolution);
    }

    private void runSequential(OptimizationProblem problem) {
        List<Solution> solutions = new ArrayList<>(colony.size());
        while (!stopRequested) {
            solutions.clear();
            for (Ant ant : colony) {
                Solution s = ant.constructSolution();
                updateBest(problem, s);
                solutions.add(s);
            }
            pheromoneTrails.update(problem, solutions);
            recordSolutions(problem, colony.size());
        }
    }

    private void runSynchronous(OptimizationProblem problem) {
        List<Callable<Solution>> tasks = new ArrayList<>(colony.size());
        for (Ant ant : colony) {
            tasks.add(ant::constructSolution);
        }

        try (ExecutorService pool = newPool(Math.min(threadCount, colony.size()))) {
            List<Solution> solutions = new ArrayList<>(colony.size());
            while (!stopRequested) {
                // Barrier: all ants construct against the same, unchanging trails.
                List<Future<Solution>> futures = pool.invokeAll(tasks);
                solutions.clear();
                for (Future<Solution> f : futures) {          // colony order -> deterministic
                    Solution s = getResult(f);
                    if (s == null) return;                    // interrupted
                    updateBest(problem, s);
                    solutions.add(s);
                }
                pheromoneTrails.update(problem, solutions);
                recordSolutions(problem, colony.size());
            }
        } catch (InterruptedException e) {
            handleInterrupt();
        }
    }

    private void runAsynchronous(OptimizationProblem problem) {
        int workers = Math.min(threadCount, colony.size());
        List<Callable<Void>> jobs = new ArrayList<>(workers);
        for (List<Ant> share : partition(colony, workers)) {
            jobs.add(() -> {
                // Round-robin over this worker's ants until the shared budget is used up.
                while (!stopRequested) {
                    for (Ant ant : share) {
                        if (stopRequested) break;
                        Solution s = ant.constructSolution();
                        updateBest(problem, s);
                        pheromoneTrails.update(problem, s);
                        recordSolutions(problem, 1);
                    }
                }
                return null;
            });
        }
        runWorkers(jobs, workers);
    }

    private void runIslands(OptimizationProblem problem) {
        int islands = Math.min(threadCount, colony.size());
        List<Callable<Void>> jobs = new ArrayList<>(islands);
        for (List<Ant> islandAnts : partition(colony, islands)) {
            PheromoneTrails islandTrails = pheromoneTrails.forColony(islandAnts.size());
            islandTrails.init(problem);
            for (Ant ant : islandAnts) {
                ant.init(problem, islandTrails, masterRng.split());
            }

            jobs.add(() -> {
                List<Solution> solutions = new ArrayList<>(islandAnts.size());
                long islandIteration = 0;
                while (!stopRequested) {
                    solutions.clear();
                    for (Ant ant : islandAnts) {
                        Solution s = ant.constructSolution();
                        updateBest(problem, s);
                        solutions.add(s);
                    }
                    islandTrails.update(problem, solutions);
                    recordSolutions(problem, islandAnts.size());

                    // Migration: pull the colony-wide best into this island's trails.
                    if (++islandIteration % migrationInterval == 0) {
                        Solution globalBest = bestSolution;
                        if (globalBest != null)
                            islandTrails.update(problem, globalBest);
                    }
                }
                return null;
            });
        }
        runWorkers(jobs, islands);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Adds {@code count} constructed solutions to the budget and evaluates the terminal
     * condition each time the running total completes another colony iteration
     * (a multiple of {@code colony.size()}).
     */
    private void recordSolutions(OptimizationProblem problem, int count) {
        long before = constructedSolutions.getAndAdd(count);
        long m = colony.size();
        long completedIterations = (before + count) / m - before / m;
        for (long i = 0; i < completedIterations && !stopRequested; i++) {
            if (isTerminated(problem))
                stopRequested = true;
        }
    }

    /** Serialised so that terminal conditions with internal state need not be thread-safe. */
    private synchronized boolean isTerminated(OptimizationProblem problem) {
        return terminalCondition.isSatisfied(problem, this);
    }

    /** Runs long-lived worker jobs to completion and rethrows the first failure, if any. */
    private void runWorkers(List<Callable<Void>> jobs, int threads) {
        // Closing the pool waits for all workers; getResult() raises stopRequested on failure
        // so that the remaining workers exit promptly.
        try (ExecutorService pool = newPool(threads)) {
            List<Future<Void>> futures = new ArrayList<>(jobs.size());
            for (Callable<Void> job : jobs) {
                futures.add(pool.submit(job));
            }
            for (Future<Void> f : futures) {
                getResult(f);
            }
        }
    }

    private <T> T getResult(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            handleInterrupt();
            return null;
        } catch (ExecutionException e) {
            stopRequested = true;
            throw new IllegalStateException("An ant failed while constructing a solution", e.getCause());
        }
    }

    private void handleInterrupt() {
        stopRequested = true;
        Thread.currentThread().interrupt(); // preserve the interrupt for the caller
        LOG.log(Level.WARNING, "ACO interrupted; returning the best solution found so far");
    }

    /** Splits {@code items} into {@code parts} round-robin shares of (almost) equal size. */
    private static <T> List<List<T>> partition(List<T> items, int parts) {
        List<List<T>> shares = new ArrayList<>(parts);
        for (int p = 0; p < parts; p++) {
            shares.add(new ArrayList<>());
        }
        for (int i = 0; i < items.size(); i++) {
            shares.get(i % parts).add(items.get(i));
        }
        return shares;
    }

    /** Pool of daemon threads so that a forgotten pool can never keep the JVM alive. */
    private static ExecutorService newPool(int threads) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "aco-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        return Executors.newFixedThreadPool(threads, factory);
    }
}
