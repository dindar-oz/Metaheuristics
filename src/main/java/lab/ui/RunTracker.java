package lab.ui;

import lab.model.LabProblem;
import metaheuristic.run.ProgressSnapshot;
import metaheuristic.run.RunHandle;
import metaheuristic.run.RunResult;
import metaheuristic.run.SearchListener;
import metaheuristic.run.StopReason;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Collects the convergence data of one run.
 * The search thread only appends improvement points and thinned metric samples to lock-free queues;
 * everything else is done on the Swing thread in drain(), so the search never waits for the UI.
 */
public final class RunTracker implements SearchListener {

    final int index;
    final String label;
    final String algorithm;
    final String parameters;
    final long seed;
    private final long minEvaluationGap; // thinning: at most ~4000 stored points per run
    private volatile RunHandle<?> handle;
    private LabProblem.StartedRun run; // Swing thread only
    private final ConcurrentLinkedQueue<double[]> pending = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ProgressSnapshot> pendingMetrics = new ConcurrentLinkedQueue<>();
    private long nextMetricSample = 0; // search thread only

    // Swing thread only
    final List<Double> evaluations = new ArrayList<>();
    final List<Double> seconds = new ArrayList<>();
    final List<Double> costs = new ArrayList<>();
    final Map<String, Series> metrics = new LinkedHashMap<>();
    private String startError;

    RunTracker(int index, String label, String algorithm, String parameters, long seed, long evaluationBudget) {
        this.index = index;
        this.label = label;
        this.algorithm = algorithm;
        this.parameters = parameters;
        this.seed = seed;
        this.minEvaluationGap = Math.max(1, evaluationBudget / 4000);
    }

    void setRun(LabProblem.StartedRun run) {
        this.run = run;
        this.handle = run.handle();
    }

    // Swing thread only
    boolean hasSolution() {
        return run != null && run.hasSolution();
    }

    // Swing thread only
    JComponent viewBestSolution() {
        return run.viewBestSolution();
    }

    void setStartError(String message) {
        this.startError = message;
    }

    // ------------------------------------------------ search thread

    @Override
    public void onImprovement(ProgressSnapshot p) {
        pending.add(new double[]{p.evaluations(), p.elapsedMillis() / 1000.0, p.bestCost()});
    }

    // metric samples are thinned by evaluations, so fast and slow runs get the same resolution
    @Override
    public void onProgress(ProgressSnapshot p) {
        if (!p.metrics().isEmpty() && p.evaluations() >= nextMetricSample) {
            nextMetricSample = p.evaluations() + minEvaluationGap;
            pendingMetrics.add(p);
        }
    }

    @Override
    public void onFinish(RunResult<?> r) {
        if (!r.finalProgress().metrics().isEmpty())
            pendingMetrics.add(r.finalProgress());
        pending.add(new double[]{r.evaluations(), r.elapsedMillis() / 1000.0, r.bestCost()});
    }

    // ------------------------------------------------ Swing thread

    // Moves new points into the series; returns true if anything changed
    boolean drain() {
        boolean changed = false;
        double[] point;
        while ((point = pending.poll()) != null) {
            append(point[0], point[1], point[2]);
            changed = true;
        }
        ProgressSnapshot p;
        while ((p = pendingMetrics.poll()) != null) {
            for (Map.Entry<String, Double> m : p.metrics().entrySet())
                metrics.computeIfAbsent(m.getKey(), k -> new Series()).add(p.evaluations(), p.elapsedMillis() / 1000.0, m.getValue());
            changed = true;
        }
        return changed;
    }

    private void append(double evals, double time, double cost) {
        int n = costs.size();
        if (n >= 2 && evals - evaluations.get(n - 2) < minEvaluationGap && costs.get(n - 1) >= cost) {
            // too close to the previous stored point: move the last point instead of adding one
            evaluations.set(n - 1, evals);
            seconds.set(n - 1, time);
            costs.set(n - 1, cost);
        } else {
            evaluations.add(evals);
            seconds.add(time);
            costs.add(cost);
        }
    }

    ProgressSnapshot latestProgress() {
        RunHandle<?> h = handle;
        return h == null ? null : h.latestProgress();
    }

    RunResult<?> result() {
        RunHandle<?> h = handle;
        if (h == null || !h.isDone() || h.result().isCompletedExceptionally())
            return null;
        return h.result().join();
    }

    boolean isDone() {
        RunHandle<?> h = handle;
        return startError != null || (h != null && h.isDone());
    }

    void cancel() {
        RunHandle<?> h = handle;
        if (h != null)
            h.cancel();
    }

    String status() {
        if (startError != null)
            return "Invalid: " + startError;
        RunHandle<?> h = handle;
        if (h == null)
            return "Waiting";
        if (!h.isDone())
            return h.latestProgress() == null ? "Starting" : "Running";
        if (h.result().isCompletedExceptionally()) {
            try {
                h.result().join();
            } catch (CompletionException e) {
                return "Failed: " + e.getCause();
            }
        }
        StopReason reason = h.result().join().stopReason();
        switch (reason) {
            case CANCELLED:
                return "Stopped";
            case ALGORITHM_FINISHED:
                return "Converged";
            default:
                return "Stop condition";
        }
    }

    /**
     * Samples of one metric (e.g. temperature) against evaluations and time.
     */
    static final class Series {
        final List<Double> evaluations = new ArrayList<>();
        final List<Double> seconds = new ArrayList<>();
        final List<Double> values = new ArrayList<>();

        void add(double evals, double time, double value) {
            evaluations.add(evals);
            seconds.add(time);
            values.add(value);
        }
    }
}
