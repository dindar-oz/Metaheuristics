package problems.pecto.aco;

import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds the ants' solutions of every iteration, taken in ant order with {@link #solution}:
 * {@link PECTOACO.ExecutionMode#SEQUENTIAL} builds each one when it is taken, on the run's thread;
 * {@link PECTOACO.ExecutionMode#SYNCHRONOUS} builds them all in parallel when the iteration starts. The ants have
 * their own RNGs and only read what they share, so both give the same solutions. The cost is computed (and cached in
 * the routes) with the solution, so that evaluating it on the run's thread only adds it up.
 */
abstract class AntBuilds implements AutoCloseable {

    static AntBuilds of(PECTOACO.ExecutionMode mode, int threads, List<Ant> ants, PECTOProblem problem) {
        return mode == PECTOACO.ExecutionMode.SYNCHRONOUS
                ? new Synchronous(ants, problem, Math.min(threads, ants.size()))
                : new Sequential(ants, problem);
    }

    final List<Ant> ants;
    final PECTOProblem problem;

    private AntBuilds(List<Ant> ants, PECTOProblem problem) {
        this.ants = ants;
        this.problem = problem;
    }

    // a new iteration: the pheromone weights have been computed
    abstract void startIteration() throws InterruptedException;

    // the solution of the ant (taken once per iteration, in ant order)
    abstract PECTOSolution solution(int ant) throws InterruptedException;

    @Override
    public abstract void close();

    final PECTOSolution build(Ant ant) {
        PECTOSolution solution = ant.construct();
        problem.cost(solution);
        return solution;
    }

    private static final class Sequential extends AntBuilds {

        Sequential(List<Ant> ants, PECTOProblem problem) {
            super(ants, problem);
        }

        @Override
        void startIteration() {
        }

        @Override
        PECTOSolution solution(int ant) {
            return build(ants.get(ant));
        }

        @Override
        public void close() {
        }
    }

    private static final class Synchronous extends AntBuilds {

        private final ExecutorService pool;
        private final List<Callable<PECTOSolution>> tasks = new ArrayList<>();
        private List<Future<PECTOSolution>> built;

        Synchronous(List<Ant> ants, PECTOProblem problem, int threads) {
            super(ants, problem);
            for (Ant ant : ants)
                tasks.add(() -> build(ant));
            // daemon threads, so that a pool can never keep the JVM alive
            AtomicInteger count = new AtomicInteger();
            pool = Executors.newFixedThreadPool(threads, r -> {
                Thread t = new Thread(r, "PECTOACO-ant-" + count.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
        }

        @Override
        void startIteration() throws InterruptedException {
            built = pool.invokeAll(tasks);
        }

        // rethrows what the ant threw
        @Override
        PECTOSolution solution(int ant) throws InterruptedException {
            try {
                return built.get(ant).get();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof RuntimeException r)
                    throw r;
                if (e.getCause() instanceof Error err)
                    throw err;
                throw new IllegalStateException(e.getCause());
            }
        }

        @Override
        public void close() {
            pool.shutdownNow();
        }
    }
}
