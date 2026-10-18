package metaheuristic.run;

import base.OptimizationProblem;
import experiments.ExperimentCase;
import metaheuristic.aco.ACO;
import metaheuristic.termination.CostBasedTC;
import metaheuristic.termination.EvaluationBasedTC;
import metaheuristic.termination.IterationBasedTC;
import metaheuristic.termination.TerminalCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import representation.IntegerPermutation;
import representation.base.Representation;
import testsupport.Fixtures;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class RunnerTest {

    static List<ExperimentCase<?, ?>> algorithms() {
        return Fixtures.allAlgorithms();
    }

    private static <S extends Representation<S>, P extends OptimizationProblem<S>> RunResult<S> run(
            ExperimentCase<S, P> c, long seed, TerminalCondition stop) {
        return c.runner().seed(seed).stopWhen(stop).run(c.getProblem());
    }

    private static <S extends Representation<S>, P extends OptimizationProblem<S>> RunResult<S> runDiagnosed(
            ExperimentCase<S, P> c, long seed, TerminalCondition stop) {
        return c.runner().seed(seed).stopWhen(stop).diagnostics(true).run(c.getProblem());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("algorithms")
    void sameSeedGivesIdenticalRun(ExperimentCase<?, ?> c) {
        RunResult<?> a = run(c, 7, new EvaluationBasedTC(3000));
        RunResult<?> b = run(c, 7, new EvaluationBasedTC(3000));
        assertEquals(a.bestCost(), b.bestCost());
        assertEquals(a.evaluations(), b.evaluations());
        assertEquals(a.iterations(), b.iterations());
        assertEquals(a.bestSolution(), b.bestSolution());
        assertEquals(7, a.seed());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("algorithms")
    void diagnosticsDoNotChangeTheRun(ExperimentCase<?, ?> c) {
        RunResult<?> plain = run(c, 5, new EvaluationBasedTC(3000));
        RunResult<?> diagnosed = runDiagnosed(c, 5, new EvaluationBasedTC(3000));
        assertEquals(plain.bestCost(), diagnosed.bestCost());
        assertEquals(plain.evaluations(), diagnosed.evaluations());
        assertEquals(plain.bestSolution(), diagnosed.bestSolution());
        assertFalse(plain.finalProgress().metrics().containsKey(Metrics.STAGNATION));
        if (diagnosed.iterations() > 0) {
            Double stagnation = diagnosed.finalProgress().metrics().get(Metrics.STAGNATION);
            assertNotNull(stagnation);
            assertTrue(stagnation >= 0 && stagnation < diagnosed.iterations());
            Double duplicates = diagnosed.finalProgress().metrics().get(Metrics.DUPLICATES);
            if (duplicates != null)
                assertTrue(duplicates >= 0 && duplicates < 1);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("algorithms")
    void evaluationBudgetIsExact(ExperimentCase<?, ?> c) {
        RunResult<?> r = run(c, 3, new EvaluationBasedTC(2500));
        if (r.stopReason() == StopReason.CONDITION_MET)
            assertEquals(2500, r.evaluations());
        else
            assertTrue(r.evaluations() <= 2500, "finished early by itself");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("algorithms")
    void bestSolutionMatchesBestCost(ExperimentCase<?, ?> c) {
        checkBest(c);
    }

    private static <S extends Representation<S>, P extends OptimizationProblem<S>> void checkBest(ExperimentCase<S, P> c) {
        RunResult<S> r = run(c, 11, new EvaluationBasedTC(2000));
        assertEquals(r.bestCost(), c.getProblem().cost(r.bestSolution()), 1e-9);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("algorithms")
    void concurrentRunsOfOneInstanceAreIndependent(ExperimentCase<?, ?> c) throws Exception {
        double expected = run(c, 5, new EvaluationBasedTC(2000)).bestCost();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<RunResult<?>>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 16; i++)
                futures.add(pool.submit(() -> run(c, 5, new EvaluationBasedTC(2000))));
            for (Future<RunResult<?>> f : futures)
                assertEquals(expected, f.get().bestCost());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void costTargetStopsRun() {
        RunResult<IntegerPermutation> r = Runner.of(ACO.builder().antCount(5).build())
                .seed(1).stopWhen(new CostBasedTC(1e9)).run(Fixtures.TSP);
        assertEquals(StopReason.CONDITION_MET, r.stopReason());
        assertEquals(1, r.evaluations()); // the first evaluation already meets the target
    }

    @Test
    void cancelStopsBackgroundRun() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            RunHandle<IntegerPermutation> handle = Runner.of(ACO.builder().antCount(5).build()).seed(1).start(Fixtures.TSP, pool);
            while (handle.latestProgress() == null)
                Thread.sleep(5);
            handle.cancel();
            RunResult<IntegerPermutation> r = handle.result().get(10, TimeUnit.SECONDS);
            assertEquals(StopReason.CANCELLED, r.stopReason());
            assertTrue(Fixtures.isPermutation(r.bestSolution(), Fixtures.TSP.getCityCount()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void listenersSeeConsistentLifecycle() {
        RecordingListener listener = new RecordingListener();
        RunResult<IntegerPermutation> r = Runner.of(ACO.builder().antCount(5).build())
                .seed(1).stopWhen(new IterationBasedTC(10)).listener(listener).run(Fixtures.TSP);
        assertEquals(1, listener.starts);
        assertEquals(10, listener.progress);
        assertTrue(listener.improvements >= 1);
        assertSame(r, listener.result);
        assertEquals(10, r.iterations());
    }

    @Test
    void resultAndSnapshotsReturnCopies() {
        RunResult<IntegerPermutation> r = Runner.of(ACO.builder().antCount(5).build())
                .seed(1).stopWhen(new IterationBasedTC(2)).run(Fixtures.TSP);
        IntegerPermutation best = r.bestSolution();
        best.swap(0, 1);
        assertNotEquals(best, r.bestSolution());
    }

    static final class RecordingListener implements SearchListener {
        int starts, progress, improvements;
        RunResult<?> result;

        @Override
        public void onStart(RunInfo info) {
            starts++;
        }

        @Override
        public void onProgress(ProgressSnapshot p) {
            progress++;
        }

        @Override
        public void onImprovement(ProgressSnapshot p) {
            improvements++;
        }

        @Override
        public void onFinish(RunResult<?> r) {
            result = r;
        }
    }
}
