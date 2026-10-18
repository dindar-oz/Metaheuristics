package experimentrunner;

import lab.model.Catalog;
import lab.model.LabProblem;
import lab.model.ParamValues;
import metaheuristic.run.ProgressSnapshot;
import metaheuristic.run.RunResult;
import metaheuristic.run.SearchListener;
import metaheuristic.run.StopReason;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The background process that runs the experiments of a workspace: {@code java experimentrunner.Worker [workspace]}.
 * One worker per workspace (it holds worker.lock). It takes the runs of the QUEUED experiments in queue order, at
 * most {@code threads} at a time, and appends every finished run to the experiment's runs.jsonl. On start it skips
 * the runs already recorded, so a worker that was stopped, killed or lost to a reboot resumes where it was; the runs
 * it was running then simply run again.
 * <p>
 * It reads queue.json about twice a second: pausing an experiment stops new runs of it (pause now also stops its
 * runs in flight, whose results are dropped), cancelling stops it for good, {@code stopWorker} ends the worker after
 * its runs in flight (AFTER_RUNS) or at once (NOW). When every run of an experiment has finished it marks it DONE and
 * writes its summary.
 */
public final class Worker {

    static final int MAX_FAILURES = 3;
    private static final long POLL_MILLIS = 500, HEARTBEAT_MILLIS = 1000, INDEX_MILLIS = 30_000;

    private final Workspace ws;
    private final List<LabProblem> catalog;
    private final String runtime;
    private final ExecutorService pool;
    private final Map<String, Experiment> experiments = new HashMap<>();
    private final Map<String, InFlight> inFlight = new LinkedHashMap<>();
    private final Object wake = new Object();
    private final long started = System.currentTimeMillis();
    private volatile boolean running = true;

    // what the worker knows about an experiment it has loaded
    private static final class Experiment {
        ExperimentSpec spec;
        LabProblem problem;
        final Map<String, LabProblem.Session> sessions = new HashMap<>();
        final Set<RunKey> done = new HashSet<>();
        int failures;
        double runMillisSum;
        int runMillisCount;
    }

    private static final class InFlight {
        final String id;
        final RunKey run;
        final long seed;
        final long startedAt = System.currentTimeMillis();
        final List<RunRecord.Trace> traces = Collections.synchronizedList(new ArrayList<>());
        LabProblem.StartedRun started;
        volatile boolean discard;

        InFlight(String id, RunKey run, long seed) {
            this.id = id;
            this.run = run;
            this.seed = seed;
        }

        String key() {
            return id + "|" + run;
        }
    }

    public Worker(Workspace ws) {
        this.ws = ws;
        this.catalog = Catalog.problems();
        this.runtime = System.getProperty("experimentrunner.runtime", "development");
        AtomicInteger count = new AtomicInteger();
        this.pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "experiment-run-" + count.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public static void main(String[] args) {
        Workspace ws = args.length > 0 ? Workspace.open(Path.of(args[0])) : Workspace.openDefault();
        if (System.console() == null)       // started in the background: the log is the only output
            try {
                PrintStream log = new PrintStream(new FileOutputStream(ws.logFile().toFile(), true), true, StandardCharsets.UTF_8);
                System.setOut(log);
                System.setErr(log);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        Worker worker = new Worker(ws);
        worker.onStart = () -> worker.tray = experimentrunner.gui.WorkerTray.install(ws);
        System.exit(worker.run() ? 0 : 1);
    }

    // the tray icon of a worker started by main(); none for embedded workers (tests)
    private Runnable onStart = () -> {
    };
    private Runnable tray = () -> {
    };

    /** Runs until asked to stop; false if another worker holds the workspace. */
    public boolean run() {
        try (FileChannel ch = FileChannel.open(ws.workerLockFile(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock = null;
            for (int attempt = 0; attempt < 10 && lock == null; attempt++) {
                try {
                    lock = ch.tryLock();
                } catch (IOException | java.nio.channels.OverlappingFileLockException e) {
                    lock = null;
                }
                if (lock == null)
                    Json.sleep(300);
            }
            if (lock == null) {
                log("another worker runs on " + ws.root() + "; exiting");
                return false;
            }
            Thread hook = new Thread(this::stopNow, "experiment-worker-shutdown");
            Runtime.getRuntime().addShutdownHook(hook);
            try {
                ws.update(q -> {
                    q.stopWorker = null;
                    return null;
                });
                log("worker started: pid " + ProcessHandle.current().pid() + ", runtime " + runtime + ", workspace " + ws.root());
                onStart.run();
                loop();
            } finally {
                tray.run();
                stopNow();
                heartbeat("stopped", ws.queue());
                Journal.writeIndex(ws, ws.queue());
                log("worker stopped");
                lock.release();
                try {
                    Runtime.getRuntime().removeShutdownHook(hook);
                } catch (IllegalStateException ignored) {
                    // the JVM is shutting down
                }
            }
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // asks the worker to end after the current poll (for tests and embedding)
    public void shutdown() {
        running = false;
        synchronized (wake) {
            wake.notifyAll();
        }
    }

    private void loop() {
        long lastBeat = 0, lastIndex = System.currentTimeMillis();
        while (running) {
            QueueState q;
            try {
                q = ws.queue();
            } catch (RuntimeException e) {
                Json.sleep(POLL_MILLIS);    // being replaced at this moment
                continue;
            }
            if (q.stopWorker == QueueState.Stop.NOW)
                break;
            synchronized (this) {
                if (q.stopWorker == QueueState.Stop.AFTER_RUNS && inFlight.isEmpty())
                    break;
                stopUnwanted(q);
                if (q.stopWorker == null)
                    dispatch(q);
            }
            long now = System.currentTimeMillis();
            if (now - lastBeat >= HEARTBEAT_MILLIS) {
                heartbeat(q.stopWorker == null ? "running" : "stopping", q);
                lastBeat = now;
            }
            if (now - lastIndex >= INDEX_MILLIS) {
                Journal.writeIndex(ws, q);
                lastIndex = now;
            }
            synchronized (wake) {
                try {
                    wake.wait(POLL_MILLIS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    // ---- scheduling

    // cancels the runs in flight of experiments that were cancelled, failed, removed or paused now
    private void stopUnwanted(QueueState q) {
        for (InFlight f : inFlight.values()) {
            QueueState.Entry e = q.has(f.id) ? q.entry(f.id) : null;
            boolean unwanted = e == null || e.state == QueueState.State.CANCELLED || e.state == QueueState.State.FAILED
                    || (e.state == QueueState.State.PAUSED && e.interrupt);
            if (unwanted && !f.discard) {
                f.discard = true;
                if (f.started != null)
                    f.started.handle().cancel();
                log("stopped " + f.key() + " (it will run again when resumed)");
            }
        }
    }

    private void dispatch(QueueState q) {
        for (QueueState.Entry e : q.experiments) {
            if (e.state != QueueState.State.QUEUED && e.state != QueueState.State.PAUSED)
                continue;
            Experiment x = load(e.id);
            if (x == null)
                continue;
            boolean pending = false;
            for (RunKey run : x.spec.runs()) {
                if (x.done.contains(run))
                    continue;
                pending = true;
                if (e.state != QueueState.State.QUEUED || q.paused || inFlight.size() >= q.threads)
                    break;
                if (!inFlight.containsKey(e.id + "|" + run))
                    start(e.id, x, run);
            }
            if (!pending && inFlight.values().stream().noneMatch(f -> f.id.equals(e.id)))
                finish(e.id, x);
        }
    }

    // the experiment as loaded from its spec and runs; null (and FAILED) if it cannot be loaded
    private Experiment load(String id) {
        Experiment x = experiments.get(id);
        if (x != null)
            return x;
        try {
            x = new Experiment();
            x.spec = ws.spec(id);
            x.spec.validate(catalog);
            x.problem = x.spec.labProblem(catalog);
            Set<RunKey> all = new HashSet<>(x.spec.runs());
            for (RunRecord r : ws.runs(id))
                if (all.contains(r.key()) && x.done.add(r.key())) {
                    x.runMillisSum += r.elapsedMs;
                    x.runMillisCount++;
                }
            experiments.put(id, x);
            writeStatus(id, x);
            return x;
        } catch (RuntimeException ex) {
            fail(id, "cannot load: " + ex.getMessage());
            return null;
        }
    }

    private void start(String id, Experiment x, RunKey run) {
        InFlight f = new InFlight(id, run, x.spec.seed(run));
        inFlight.put(f.key(), f);
        try {
            LabProblem.Session session = x.sessions.computeIfAbsent(run.instance(),
                    label -> x.problem.newSession(x.spec.problemValues(x.problem, x.spec.instance(label))));
            ParamValues values = x.spec.algorithmValues(x.problem, x.spec.config(run.config()));
            int every = x.spec.traceEvery;
            SearchListener listener = new SearchListener() {
                @Override
                public void onProgress(ProgressSnapshot s) {
                    if (every > 0 && s.iterations() % every == 0)
                        f.traces.add(trace(run, s));
                }
            };
            f.started = session.start(x.spec.algorithm, values, f.seed, x.spec.stopCondition(), listener, pool);
            f.started.handle().result().whenComplete((result, error) -> finished(f, result, error));
            writeStatus(id, x);
        } catch (RuntimeException ex) {
            finished(f, null, ex);
        }
    }

    private static RunRecord.Trace trace(RunKey run, ProgressSnapshot s) {
        RunRecord.Trace t = new RunRecord.Trace();
        t.config = run.config();
        t.instance = run.instance();
        t.repeat = run.repeat();
        t.iteration = s.iterations();
        t.evaluations = s.evaluations();
        t.elapsedMs = s.elapsedMillis();
        t.bestCost = s.bestCost();
        t.metrics.putAll(s.metrics());
        return t;
    }

    // a run ended: record it, or drop it if it was stopped
    private synchronized void finished(InFlight f, RunResult<?> result, Throwable error) {
        inFlight.remove(f.key());
        Experiment x = experiments.get(f.id);
        if (x == null)
            return;
        if (f.discard || (result != null && result.stopReason() == StopReason.CANCELLED)) {
            // stopped on purpose: it runs again later
        } else if (error != null || result == null) {
            x.failures++;
            StringWriter trace = new StringWriter();
            if (error != null)
                error.printStackTrace(new PrintWriter(trace));
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("run", f.run.toString());
            line.put("seed", f.seed);
            line.put("time", System.currentTimeMillis());
            line.put("error", trace.toString());
            ws.appendError(f.id, Json.LINE.toJson(line));
            log("run " + f.key() + " failed: " + (error == null ? "no result" : error));
            if (x.failures >= MAX_FAILURES)
                fail(f.id, x.failures + " runs failed; last: " + (error == null ? "no result" : error.toString()));
        } else {
            RunRecord r = new RunRecord();
            r.config = f.run.config();
            r.instance = f.run.instance();
            r.repeat = f.run.repeat();
            r.seed = f.seed;
            r.bestCost = result.bestCost();
            r.evaluations = result.evaluations();
            r.iterations = result.iterations();
            r.elapsedMs = result.elapsedMillis();
            r.timeToBestMs = result.timeToBestMillis();
            r.stopReason = String.valueOf(result.stopReason());
            r.metrics.putAll(result.finalProgress().metrics());
            r.runtime = runtime;
            r.finished = System.currentTimeMillis();
            ws.appendTraces(f.id, f.traces);    // first the trace: a run without its record runs again
            ws.appendRun(f.id, r);
            x.done.add(f.run);
            x.runMillisSum += r.elapsedMs;
            x.runMillisCount++;
        }
        writeStatus(f.id, x);
        synchronized (wake) {
            wake.notifyAll();
        }
    }

    // every run finished: DONE, with its summary
    private void finish(String id, Experiment x) {
        ws.update(q -> {
            if (q.has(id)) {
                QueueState.Entry e = q.entry(id);
                e.state = QueueState.State.DONE;
                e.interrupt = false;
                e.finished = Journal.time(System.currentTimeMillis());
            }
            return null;
        });
        try {
            Summary.write(ws, id);
        } catch (RuntimeException ex) {
            log("summary of " + id + " failed: " + ex);
        }
        experiments.remove(id);
        log("experiment " + id + " done (" + x.done.size() + " runs)");
    }

    private void fail(String id, String message) {
        ws.update(q -> {
            if (q.has(id)) {
                QueueState.Entry e = q.entry(id);
                e.state = QueueState.State.FAILED;
                e.message = message;
            }
            return null;
        });
        experiments.remove(id);
        log("experiment " + id + " failed: " + message);
    }

    private void stopNow() {
        synchronized (this) {
            for (InFlight f : inFlight.values()) {
                f.discard = true;
                if (f.started != null)
                    f.started.handle().cancel();
            }
        }
        // give the runs a moment to see the cancel, so that none is half recorded
        for (int i = 0; i < 50 && !inFlightEmpty(); i++)
            Json.sleep(100);
        pool.shutdownNow();
    }

    private synchronized boolean inFlightEmpty() {
        return inFlight.isEmpty();
    }

    // ---- reporting

    private void writeStatus(String id, Experiment x) {
        ExperimentStatus s = new ExperimentStatus();
        s.total = x.spec.runs().size();
        s.done = x.done.size();
        s.running = (int) inFlight.values().stream().filter(f -> f.id.equals(id)).count();
        s.failures = x.failures;
        s.updated = System.currentTimeMillis();
        s.meanRunMillis = x.runMillisCount == 0 ? 0 : x.runMillisSum / x.runMillisCount;
        int threads;
        try {
            threads = Math.max(1, ws.queue().threads);
        } catch (RuntimeException e) {
            threads = 1;
        }
        s.etaMillis = x.runMillisCount == 0 ? -1 : (long) ((s.total - s.done) * s.meanRunMillis / Math.min(threads, Math.max(1, s.total - s.done)));
        try {
            ws.writeStatus(id, s);
        } catch (RuntimeException e) {
            log("cannot write the status of " + id + ": " + e);
        }
    }

    private void heartbeat(String state, QueueState q) {
        WorkerInfo w = new WorkerInfo();
        w.pid = ProcessHandle.current().pid();
        w.state = state;
        w.started = started;
        w.heartbeat = System.currentTimeMillis();
        w.threads = q.threads;
        w.runtime = runtime;
        synchronized (this) {
            for (InFlight f : inFlight.values()) {
                WorkerInfo.Running r = new WorkerInfo.Running();
                r.experiment = f.id;
                r.run = f.run.toString();
                r.seed = f.seed;
                r.started = f.startedAt;
                ProgressSnapshot p = f.started == null ? null : f.started.handle().latestProgress();
                if (p != null) {
                    r.iterations = p.iterations();
                    r.evaluations = p.evaluations();
                    r.bestCost = p.bestCost();
                }
                w.running.add(r);
            }
        }
        try {
            ws.writeWorker(w);
        } catch (RuntimeException e) {
            log("cannot write worker.json: " + e);
        }
    }

    private static void log(String message) {
        System.out.println(Journal.time(System.currentTimeMillis()) + "  " + message);
    }
}
