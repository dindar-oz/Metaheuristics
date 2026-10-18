package experimentrunner;

import lab.model.Catalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What the CLI and the GUI can do with a workspace. Every change goes through {@link Workspace#update}; the worker
 * picks it up within a second.
 */
public final class Control {

    private final Workspace ws;

    public Control(Workspace ws) {
        this.ws = ws;
    }

    public Workspace workspace() {
        return ws;
    }

    /** Checks the spec against the lab's catalog; returns it with its defaults filled in. */
    public ExperimentSpec validate(Path specFile) {
        ExperimentSpec spec = Json.read(specFile, ExperimentSpec.class);
        if (spec == null)
            throw new IllegalArgumentException(specFile + " is empty");
        spec.validate(Catalog.problems());
        return spec;
    }

    /** Adds the experiment at the end of the queue: its directory, spec.json and JOURNAL.md. */
    public ExperimentSpec submit(Path specFile) {
        ExperimentSpec spec = validate(specFile);
        if (ws.queue().has(spec.id) || Files.exists(ws.dir(spec.id)))
            throw new IllegalArgumentException("experiment " + spec.id + " exists already; choose another id");
        try {
            Files.createDirectories(ws.dir(spec.id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Json.write(ws.specFile(spec.id), spec);
        Journal.create(ws, spec);
        ExperimentStatus s = new ExperimentStatus();
        s.total = spec.runs().size();
        s.etaMillis = -1;
        s.updated = System.currentTimeMillis();
        ws.writeStatus(spec.id, s);
        ws.update(q -> {
            QueueState.Entry e = new QueueState.Entry();
            e.id = spec.id;
            e.submitted = Journal.time(System.currentTimeMillis());
            q.experiments.add(e);
            return null;
        });
        return spec;
    }

    /** No new runs of the experiment; with now, its runs in flight stop too (and run again on resume). */
    public void pause(String id, boolean now) {
        ws.update(q -> {
            QueueState.Entry e = q.entry(id);
            if (e.state == QueueState.State.QUEUED || e.state == QueueState.State.PAUSED) {
                e.state = QueueState.State.PAUSED;
                e.interrupt = e.interrupt || now;
            } else
                throw new IllegalStateException(id + " is " + e.state + ", it cannot be paused");
            return null;
        });
    }

    /** Back to QUEUED, from PAUSED, FAILED or CANCELLED; it continues with the runs it has not finished. */
    public void resume(String id) {
        ws.update(q -> {
            QueueState.Entry e = q.entry(id);
            if (e.state == QueueState.State.DONE)
                throw new IllegalStateException(id + " is done");
            e.state = QueueState.State.QUEUED;
            e.interrupt = false;
            e.message = null;
            return null;
        });
    }

    /** Stops it for good (its runs in flight too); its results are kept and it can still be resumed. */
    public void cancel(String id) {
        ws.update(q -> {
            QueueState.Entry e = q.entry(id);
            if (e.state != QueueState.State.DONE)
                e.state = QueueState.State.CANCELLED;
            return null;
        });
    }

    /** Moves it delta places towards the front (negative) or the back of the queue. */
    public void move(String id, int delta) {
        ws.update(q -> {
            QueueState.Entry e = q.entry(id);
            int i = q.experiments.indexOf(e);
            int j = Math.max(0, Math.min(q.experiments.size() - 1, i + delta));
            q.experiments.remove(i);
            q.experiments.add(j, e);
            return null;
        });
    }

    public void threads(int threads) {
        if (threads < 1)
            throw new IllegalArgumentException("threads must be at least 1");
        ws.update(q -> {
            q.threads = threads;
            return null;
        });
    }

    /** No new runs of any experiment (the runs in flight finish), or again new runs. */
    public void pauseAll(boolean paused) {
        ws.update(q -> {
            q.paused = paused;
            return null;
        });
    }

    public long startWorker(String maxHeap) {
        return WorkerLauncher.start(ws, maxHeap);
    }

    /** The worker ends after its runs in flight, or at once (they run again when it starts next). */
    public void stopWorker(boolean now) {
        ws.update(q -> {
            q.stopWorker = now ? QueueState.Stop.NOW : QueueState.Stop.AFTER_RUNS;
            return null;
        });
    }

    public boolean workerRunning() {
        return ws.workerAlive();
    }

    /** The summary of the runs finished so far; also written to summary.md and the journal. */
    public Summary summarize(String id) {
        return Summary.write(ws, id);
    }
}
