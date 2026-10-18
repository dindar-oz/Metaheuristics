package experimentrunner;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The directory of the runner and its files (see the package documentation). Every change of queue.json goes
 * through {@link #update}, which holds queue.lock, so the CLI, the GUI and the worker can change it at the same time.
 */
public final class Workspace {

    private final Path root;

    private Workspace(Path root) {
        this.root = root;
    }

    // the workspace at root, created if needed
    public static Workspace open(Path root) {
        Workspace ws = new Workspace(root.toAbsolutePath().normalize());
        try {
            Files.createDirectories(ws.root);
            if (!Files.exists(ws.queueFile()))
                ws.update(q -> null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ws;
    }

    // the workspace given by the EXPERIMENTS_DIR environment variable, or experiments/ in the working directory
    public static Workspace openDefault() {
        String dir = System.getenv("EXPERIMENTS_DIR");
        return open(Path.of(dir == null || dir.isBlank() ? "experiments" : dir));
    }

    public Path root() {
        return root;
    }

    Path queueFile() {
        return root.resolve("queue.json");
    }

    Path workerLockFile() {
        return root.resolve("worker.lock");
    }

    Path workerFile() {
        return root.resolve("worker.json");
    }

    public Path logFile() {
        return root.resolve("worker.log");
    }

    public Path indexFile() {
        return root.resolve("INDEX.md");
    }

    Path runtimeDir() {
        return root.resolve("runtime");
    }

    public Path dir(String id) {
        return root.resolve(id);
    }

    public Path specFile(String id) {
        return dir(id).resolve("spec.json");
    }

    public Path journalFile(String id) {
        return dir(id).resolve("JOURNAL.md");
    }

    public Path summaryFile(String id) {
        return dir(id).resolve("summary.md");
    }

    Path runsFile(String id) {
        return dir(id).resolve("runs.jsonl");
    }

    Path traceFile(String id) {
        return dir(id).resolve("trace.jsonl");
    }

    Path errorsFile(String id) {
        return dir(id).resolve("errors.jsonl");
    }

    Path statusFile(String id) {
        return dir(id).resolve("status.json");
    }

    // ---- queue

    public QueueState queue() {
        return Files.exists(queueFile()) ? Json.read(queueFile(), QueueState.class) : new QueueState();
    }

    /**
     * Reads queue.json, applies the change and writes it back, all under queue.lock; returns what the change returns.
     * Also refreshes INDEX.md.
     */
    public <T> T update(Function<QueueState, T> change) {
        T result;
        QueueState q;
        synchronized (Workspace.class) {    // FileLock is per JVM: threads of one JVM must take turns themselves
            try (FileChannel ch = FileChannel.open(root.resolve("queue.lock"), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
                 FileLock ignored = ch.lock()) {
                q = queue();
                result = change.apply(q);
                Json.write(queueFile(), q);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        Journal.writeIndex(this, q);
        return result;
    }

    public ExperimentSpec spec(String id) {
        return Json.read(specFile(id), ExperimentSpec.class);
    }

    public ExperimentStatus status(String id) {
        Path f = statusFile(id);
        return Files.exists(f) ? Json.read(f, ExperimentStatus.class) : null;
    }

    void writeStatus(String id, ExperimentStatus status) {
        Json.write(statusFile(id), status);
    }

    // ---- worker

    public WorkerInfo worker() {
        try {
            return Files.exists(workerFile()) ? Json.read(workerFile(), WorkerInfo.class) : null;
        } catch (RuntimeException e) {
            return null;    // being replaced at this moment
        }
    }

    void writeWorker(WorkerInfo info) {
        Json.write(workerFile(), info);
    }

    // true if a worker holds worker.lock, i.e. is alive (whatever its heartbeat says)
    public boolean workerAlive() {
        synchronized (Workspace.class) {
            try (FileChannel ch = FileChannel.open(workerLockFile(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                FileLock lock = ch.tryLock();
                if (lock == null)
                    return true;
                lock.release();
                return false;
            } catch (java.nio.channels.OverlappingFileLockException e) {
                return true;        // the worker runs in this JVM (tests)
            } catch (IOException e) {
                return true;        // Windows: the file is locked by another process
            }
        }
    }

    // ---- results

    void appendRun(String id, RunRecord run) {
        appendLines(runsFile(id), List.of(Json.LINE.toJson(run)));
    }

    void appendTraces(String id, List<RunRecord.Trace> traces) {
        if (!traces.isEmpty())
            appendLines(traceFile(id), traces.stream().map(Json.LINE::toJson).toList());
    }

    void appendError(String id, String line) {
        appendLines(errorsFile(id), List.of(line));
    }

    private static synchronized void appendLines(Path file, List<String> lines) {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            for (String line : lines) {
                w.write(line);
                w.newLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // the finished runs; a line cut off by a crash is skipped (that run will run again)
    public List<RunRecord> runs(String id) {
        return readLines(runsFile(id), RunRecord.class);
    }

    public List<RunRecord.Trace> traces(String id) {
        return readLines(traceFile(id), RunRecord.Trace.class);
    }

    private static <T> List<T> readLines(Path file, Class<T> type) {
        List<T> out = new ArrayList<>();
        if (!Files.exists(file))
            return out;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank())
                    continue;
                try {
                    out.add(Json.LINE.fromJson(line, type));
                } catch (RuntimeException e) {
                    // an incomplete last line
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }
}
