package experimentrunner;

import lab.model.Catalog;
import lab.model.LabProblem;
import metaheuristic.run.RunResult;
import metaheuristic.run.SearchListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WorkerTest {

    @TempDir
    Path dir;

    private Path specFile(ExperimentSpec spec) {
        Path f = dir.resolve(spec.id + ".json");
        Json.write(f, spec);
        return f;
    }

    private static Thread startWorker(Workspace ws) {
        Thread t = new Thread(() -> new Worker(ws).run(), "test-worker");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void await(String what, BooleanSupplier condition) {
        long end = System.currentTimeMillis() + 120_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end)
                fail("timed out waiting for " + what);
            Json.sleep(50);
        }
    }

    private static QueueState.State state(Workspace ws, String id) {
        return ws.queue().entry(id).state;
    }

    // the result of the run when it is run directly, outside the worker
    private static double direct(ExperimentSpec spec, RunKey run) {
        List<LabProblem> catalog = Catalog.problems();
        spec.validate(catalog);
        LabProblem p = spec.labProblem(catalog);
        LabProblem.Session s = p.newSession(spec.problemValues(p, spec.instance(run.instance())));
        LabProblem.StartedRun r = s.start(spec.algorithm, spec.algorithmValues(p, spec.config(run.config())), spec.seed(run),
                spec.stopCondition(), new SearchListener() {
                }, Runnable::run);
        return ((RunResult<?>) r.handle().result().join()).bestCost();
    }

    @Test
    void runsAnExperimentToTheEndAndSummarizesIt() {
        Workspace ws = Workspace.open(dir.resolve("ws"));
        Control c = new Control(ws);
        c.threads(2);
        ExperimentSpec spec = ExperimentSpecTest.tsp("tsp-run", 3, 3000);
        c.submit(specFile(spec));
        assertTrue(Files.exists(ws.journalFile("tsp-run")));
        assertTrue(Summary.read(ws.journalFile("tsp-run")).contains("Does slower cooling help?"));

        Thread w = startWorker(ws);
        await("DONE", () -> state(ws, "tsp-run") == QueueState.State.DONE);
        c.stopWorker(false);
        await("the worker to stop", () -> !w.isAlive());

        List<RunRecord> runs = ws.runs("tsp-run");
        assertEquals(12, runs.size());
        assertEquals(12, new HashSet<>(runs.stream().map(RunRecord::key).toList()).size());
        for (RunRecord r : runs.subList(0, 3))
            assertEquals(direct(ExperimentSpecTest.tsp("tsp-run", 3, 3000), r.key()), r.bestCost, "run " + r.key());
        assertFalse(ws.traces("tsp-run").isEmpty());
        String journal = Summary.read(ws.journalFile("tsp-run"));
        assertTrue(journal.contains("12 of 12 runs finished"), journal);
        assertTrue(journal.contains("## Conclusions"));
        assertTrue(Files.exists(ws.summaryFile("tsp-run")));
        assertTrue(Summary.read(ws.indexFile()).contains("tsp-run"));
        ExperimentStatus s = ws.status("tsp-run");
        assertEquals(12, s.done);
        assertEquals(0, s.running);
    }

    @Test
    void resumesAfterTheWorkerStopsWithoutRepeatingOrLosingRuns() {
        Workspace ws = Workspace.open(dir.resolve("ws"));
        Control c = new Control(ws);
        c.threads(3);
        c.submit(specFile(ExperimentSpecTest.tsp("tsp-stop", 8, 40_000)));   // 32 runs

        Thread w = startWorker(ws);
        await("a few runs", () -> ws.runs("tsp-stop").size() >= 4);
        c.stopWorker(true);                     // runs in flight are dropped
        await("the worker to stop", () -> !w.isAlive());
        int before = ws.runs("tsp-stop").size();
        assertFalse(ws.workerAlive());

        Thread again = startWorker(ws);
        await("DONE", () -> state(ws, "tsp-stop") == QueueState.State.DONE);
        c.stopWorker(false);
        await("the worker to stop", () -> !again.isAlive());

        List<RunRecord> runs = ws.runs("tsp-stop");
        Set<RunKey> keys = new HashSet<>(runs.stream().map(RunRecord::key).toList());
        assertEquals(32, keys.size());
        assertEquals(32, runs.size(), "no run recorded twice (" + before + " before the stop)");
        RunRecord last = runs.get(runs.size() - 1);
        assertEquals(direct(ExperimentSpecTest.tsp("tsp-stop", 8, 40_000), last.key()), last.bestCost);
    }

    @Test
    void pausedExperimentsWaitAndTheQueueOrderHolds() {
        Workspace ws = Workspace.open(dir.resolve("ws"));
        Control c = new Control(ws);
        c.threads(2);
        c.submit(specFile(ExperimentSpecTest.tsp("first", 2, 2000)));
        c.submit(specFile(ExperimentSpecTest.tsp("second", 2, 2000)));
        c.pause("first", false);

        Thread w = startWorker(ws);
        await("second DONE", () -> state(ws, "second") == QueueState.State.DONE);
        assertEquals(QueueState.State.PAUSED, state(ws, "first"));
        assertEquals(0, ws.runs("first").size());

        c.resume("first");
        await("first DONE", () -> state(ws, "first") == QueueState.State.DONE);
        assertEquals(8, ws.runs("first").size());

        c.cancel("second");                     // DONE stays DONE
        assertEquals(QueueState.State.DONE, state(ws, "second"));
        c.stopWorker(false);
        await("the worker to stop", () -> !w.isAlive());
    }

    @Test
    void rejectsADuplicateIdAndASecondWorker() throws Exception {
        Workspace ws = Workspace.open(dir.resolve("ws"));
        Control c = new Control(ws);
        Path f = specFile(ExperimentSpecTest.tsp("dup", 1, 500));
        c.submit(f);
        try {
            c.submit(f);
            fail("a second submit of the same id must fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("exists"));
        }
        c.pause("dup", false);
        Thread w = startWorker(ws);
        await("the worker", ws::workerAlive);
        assertFalse(new Worker(ws).run(), "a second worker must not take the workspace");
        c.stopWorker(false);
        w.join(30_000);
        assertFalse(w.isAlive());
    }
}
