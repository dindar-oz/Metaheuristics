package problems.pecto.aco.gui;

import problems.pecto.PECTOProblem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Runs a diagnosed PECTOACO run on a worker thread and lets the GUI drive it: step to the next event of a level, play
 * (with a delay per event, or as fast as possible with a frame every few dozen milliseconds), pause, run to the end of
 * an iteration, step back and restart. The GUI reads {@link #latest()} and {@link #status()}.
 * <p>
 * Going back works by determinism: the run is restored from the nearest checkpoint (taken at the start of
 * iterations) and replayed silently up to the wanted event. To step back at a level, a first replay finds the previous
 * event of that level and a second one stops there. Checkpoints are thinned (every 2nd, 4th, ... iteration) when they
 * outgrow {@link #CHECKPOINT_BUDGET}, which only makes replays longer.
 * <p>
 * The commands are made on the Swing thread and never block it: a command that needs another run starts a new worker,
 * which waits for the old one to notice that it is superseded (at its next event) and end.
 */
final class RunController {

    enum Status { STARTING, RUNNING, PAUSED, BUSY, FINISHED, FAILED }

    private enum Mode { STEP, PLAY, TO_SEQ, TO_ITERATION }

    static final long CHECKPOINT_BUDGET = 512L << 20;
    private static final long PLAY_FRAME_MILLIS = 40;

    final PECTOProblem problem;
    final ColonySettings settings;
    final long seed;
    final Candidates candidates;

    private final Object lock = new Object();
    private final AtomicReference<Frame> latest = new AtomicReference<>();
    private final TreeMap<Integer, Checkpoint> checkpoints = new TreeMap<>();   // guarded by itself
    private long checkpointBytes;
    private int stride = 1;

    private volatile int generation;                 // the current worker's; older workers end at their next event
    private volatile Mode mode = Mode.STEP;
    private volatile EventType.Level level = EventType.Level.ITERATION;
    private volatile int delayMillis;
    private volatile long targetSeq;
    private volatile int targetIteration;
    private volatile long lastPublish;
    // guarded by lock
    private Status status = Status.STARTING;
    private boolean resume;
    private Thread worker;
    private String failure;

    /** Superseded by a newer command: the worker ends. */
    private static final class Rewind extends RuntimeException {
        Rewind() {
            super(null, null, false, false);
        }
    }

    /** A scan for the previous event reached the current one. */
    private static final class ScanDone extends RuntimeException {
        ScanDone() {
            super(null, null, false, false);
        }
    }

    RunController(PECTOProblem problem, ColonySettings settings, long seed) {
        settings.toPECTOACO();      // checks the values
        this.problem = problem;
        this.settings = settings;
        this.seed = seed;
        this.candidates = new Candidates(problem, settings.candidates());
    }

    // ---- reading (any thread)

    Frame latest() {
        return latest.get();
    }

    Status status() {
        synchronized (lock) {
            return status;
        }
    }

    String failure() {
        synchronized (lock) {
            return failure;
        }
    }

    EventType.Level level() {
        return level;
    }

    boolean playing() {
        return mode == Mode.PLAY;
    }

    String checkpointInfo() {
        synchronized (checkpoints) {
            return checkpoints.size() + " checkpoints" + (stride > 1 ? ", every " + stride + " iterations" : "");
        }
    }

    // waits until the run pauses, finishes or fails (for tests); false on timeout
    boolean awaitStop(long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        synchronized (lock) {
            while (status != Status.PAUSED && status != Status.FINISHED && status != Status.FAILED) {
                long left = end - System.currentTimeMillis();
                if (left <= 0)
                    return false;
                lock.wait(left);
            }
            return true;
        }
    }

    // ---- commands (Swing thread)

    void setLevel(EventType.Level level) {
        this.level = level;
    }

    void setDelay(int millis) {
        synchronized (lock) {
            delayMillis = millis;
            lock.notifyAll();
        }
    }

    // a new run, paused at its first event
    void start() {
        launch(Mode.STEP, 0, 0, gen -> runFrom(null, new WorkerProbe(gen)));
    }

    void restart() {
        start();
    }

    // on to the next event of the level
    void step() {
        synchronized (lock) {
            if (status != Status.PAUSED)
                return;
            mode = Mode.STEP;
            resumeLocked();
        }
    }

    void play() {
        synchronized (lock) {
            if (status != Status.PAUSED && status != Status.RUNNING)
                return;
            mode = Mode.PLAY;
            lastPublish = 0;
            if (status == Status.PAUSED)
                resumeLocked();
            lock.notifyAll();
        }
    }

    // stops at the next event of the level
    void pause() {
        synchronized (lock) {
            if (mode == Mode.PLAY || mode == Mode.TO_ITERATION) {
                mode = Mode.STEP;
                lock.notifyAll();
            }
        }
    }

    // to the end of the iteration, forward or back
    void runToIteration(int iteration) {
        Frame f = latest.get();
        synchronized (lock) {
            if (status == Status.BUSY || status == Status.STARTING || status == Status.FAILED || f == null)
                return;
            int done = f.type() == EventType.ITERATION_DONE ? f.iteration() : f.iteration() - 1;
            if (iteration > done && status != Status.FINISHED) {
                targetIteration = iteration;
                mode = Mode.TO_ITERATION;
                if (status == Status.PAUSED)
                    resumeLocked();
                lock.notifyAll();
                return;
            }
        }
        Checkpoint from = floor(iteration);
        launch(Mode.TO_ITERATION, 0, iteration, gen -> runFrom(from, new WorkerProbe(gen)));
    }

    // to the previous event of the level
    void back() {
        Frame f = latest.get();
        synchronized (lock) {
            if (f == null || status == Status.BUSY || status == Status.STARTING || f.seq() <= 1)
                return;
        }
        long current = f.seq();
        EventType.Level at = level;
        launch(Mode.TO_SEQ, current, 0, gen -> stepBack(gen, current, at));
    }

    // ends the worker
    void dispose() {
        synchronized (lock) {
            generation++;
            lock.notifyAll();
        }
    }

    private void resumeLocked() {
        resume = true;
        status = Status.RUNNING;
        lock.notifyAll();
    }

    // ---- workers

    private interface Task {
        void run(int generation);
    }

    private void launch(Mode m, long seq, int iteration, Task task) {
        synchronized (lock) {
            int gen = ++generation;
            Thread old = worker;
            mode = m;
            targetSeq = seq;
            targetIteration = iteration;
            status = Status.BUSY;
            resume = false;
            failure = null;
            lock.notifyAll();
            worker = new Thread(() -> work(gen, old, task), "PECTOACO-diagnosis-" + gen);
            worker.setDaemon(true);
            worker.start();
        }
    }

    private void work(int gen, Thread old, Task task) {
        try {
            if (old != null)
                old.join();
        } catch (InterruptedException e) {
            return;
        }
        try {
            task.run(gen);
            synchronized (lock) {
                if (gen == generation) {
                    status = Status.FINISHED;
                    lock.notifyAll();
                }
            }
        } catch (Rewind superseded) {
            // a newer command took over
        } catch (RuntimeException | Error e) {
            synchronized (lock) {
                if (gen == generation) {
                    status = Status.FAILED;
                    failure = e.toString();
                    lock.notifyAll();
                }
            }
            e.printStackTrace();
        }
    }

    // a run from the checkpoint, or from the start; it runs until a newer command ends it
    private void runFrom(Checkpoint from, WorkerProbe probe) {
        ColonyRun run = new ColonyRun(settings, problem, candidates, new ColonyContext(problem, seed, 0), probe);
        synchronized (lock) {
            if (probe.gen == generation && status == Status.BUSY && from == null && mode == Mode.STEP)
                status = Status.RUNNING;
        }
        if (from == null) {
            if (run.start())
                run.iterate();
        } else {
            run.restore(from);
            run.iterate();
        }
    }

    // finds the last event of the level before `current` and stops there
    private void stepBack(int gen, long current, EventType.Level at) {
        WorkerProbe probe = new WorkerProbe(gen);
        long target = -1;
        List<Checkpoint> starts = new ArrayList<>(before(current));
        starts.add(null);
        for (Checkpoint from : starts) {
            probe.scan(current, at);
            try {
                runFrom(from, probe);
            } catch (ScanDone done) {
                // reached the current event
            }
            if (!probe.scanned.isEmpty()) {
                target = probe.scanned.get(probe.scanned.size() - 1);
                break;
            }
        }
        probe.scanning = false;
        if (target < 0)
            target = current;
        synchronized (lock) {
            if (gen != generation)
                throw new Rewind();
            mode = Mode.TO_SEQ;
            targetSeq = target;
        }
        long t = target;
        Checkpoint from = before(t).stream().findFirst().orElse(null);
        runFrom(from, probe);
    }

    /** The probe of one worker; it ends its run as soon as a newer worker exists. */
    private final class WorkerProbe implements Probe {
        final int gen;
        boolean scanning;
        private long scanUntil;
        private EventType.Level scanLevel;
        final List<Long> scanned = new ArrayList<>();

        WorkerProbe(int gen) {
            this.gen = gen;
        }

        void scan(long until, EventType.Level at) {
            scanning = true;
            scanUntil = until;
            scanLevel = at;
            scanned.clear();
        }

        @Override
        public boolean frameAt(EventType type, long seq, int iteration) {
            if (gen != generation)
                throw new Rewind();
            if (scanning) {
                if (seq >= scanUntil)
                    throw new ScanDone();
                if (type.stopsAt(scanLevel))
                    scanned.add(seq);
                return false;
            }
            return switch (mode) {
                case STEP -> type.stopsAt(level);
                case PLAY -> type.stopsAt(level)
                        && (delayMillis > 0 || System.currentTimeMillis() - lastPublish >= PLAY_FRAME_MILLIS);
                case TO_SEQ -> seq == targetSeq;
                case TO_ITERATION -> type == EventType.ITERATION_DONE && iteration == targetIteration;
            };
        }

        @Override
        public void deliver(Frame frame) {
            synchronized (lock) {
                if (gen != generation)
                    throw new Rewind();
                latest.set(frame);
                if (mode == Mode.PLAY) {
                    lastPublish = System.currentTimeMillis();
                    long end = lastPublish + delayMillis;
                    while (mode == Mode.PLAY && gen == generation && System.currentTimeMillis() < end)
                        waitLocked(end - System.currentTimeMillis());
                    if (gen != generation)
                        throw new Rewind();
                    if (mode == Mode.PLAY) {
                        if (status != Status.RUNNING) {
                            status = Status.RUNNING;
                            lock.notifyAll();
                        }
                        return;
                    }
                }
                // a stop: STEP, a target reached, or a pause during play
                status = Status.PAUSED;
                resume = false;
                lock.notifyAll();
                while (!resume && gen == generation)
                    waitLocked(0);
                if (gen != generation)
                    throw new Rewind();
            }
        }

        @Override
        public void iterationStarts(int iteration, long seq, Supplier<Checkpoint> checkpoint) {
            offer(iteration, checkpoint);
        }
    }

    private void waitLocked(long millis) {
        try {
            lock.wait(Math.max(0, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Rewind();
        }
    }

    // ---- checkpoints

    private void offer(int iteration, Supplier<Checkpoint> checkpoint) {
        synchronized (checkpoints) {
            if (checkpoints.containsKey(iteration) || iteration % stride != 0)
                return;
            Checkpoint cp = checkpoint.get();
            checkpoints.put(iteration, cp);
            checkpointBytes += cp.bytes();
            while (checkpointBytes > CHECKPOINT_BUDGET && stride < (1 << 20)) {
                stride *= 2;
                for (var it = checkpoints.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<Integer, Checkpoint> e = it.next();
                    if (e.getKey() % stride != 0) {
                        checkpointBytes -= e.getValue().bytes();
                        it.remove();
                    }
                }
            }
        }
    }

    // the latest checkpoint at or before the start of the iteration (null: the start of the run)
    private Checkpoint floor(int iteration) {
        synchronized (checkpoints) {
            Map.Entry<Integer, Checkpoint> e = checkpoints.floorEntry(iteration);
            return e == null ? null : e.getValue();
        }
    }

    // the checkpoints taken before the event, latest first
    private List<Checkpoint> before(long seq) {
        synchronized (checkpoints) {
            return checkpoints.values().stream().filter(cp -> cp.seq() < seq)
                    .sorted(Comparator.comparingLong(Checkpoint::seq).reversed()).toList();
        }
    }
}
