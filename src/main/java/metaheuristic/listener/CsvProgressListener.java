package metaheuristic.listener;

import metaheuristic.run.ProgressSnapshot;
import metaheuristic.run.RunInfo;
import metaheuristic.run.RunResult;
import metaheuristic.run.SearchListener;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the convergence trace of ONE run as CSV (iteration, evaluations, elapsedMs, bestCost, metrics...).
 * Create one instance per run, e.g. Runner.listenerPerRun(info -> new CsvProgressListener(dir.resolve(info.seed() + ".csv"))).
 * The file stays open for the whole run and is closed in onFinish(). I/O errors are rethrown (they abort the run).
 */
public final class CsvProgressListener implements SearchListener {
    private final Path file;
    private PrintWriter out;
    private List<String> metricNames = null;

    public CsvProgressListener(Path file) {
        this.file = file;
    }

    @Override
    public void onStart(RunInfo info) {
        try {
            if (file.getParent() != null)
                Files.createDirectories(file.getParent());
            out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.println("# " + info);
    }

    @Override
    public void onProgress(ProgressSnapshot p) {
        if (metricNames == null) {
            metricNames = new ArrayList<>(p.metrics().keySet());
            StringBuilder header = new StringBuilder("iteration,evaluations,elapsedMs,bestCost");
            metricNames.forEach(m -> header.append(',').append(m));
            out.println(header);
        }
        StringBuilder row = new StringBuilder()
                .append(p.iterations()).append(',').append(p.evaluations()).append(',')
                .append(p.elapsedMillis()).append(',').append(p.bestCost());
        for (String m : metricNames) {
            Double v = p.metrics().get(m);
            row.append(',').append(v == null ? "" : v.toString());
        }
        out.println(row);
    }

    @Override
    public void onFinish(RunResult<?> result) {
        out.println("# " + result);
        out.close();
        if (out.checkError())
            throw new UncheckedIOException(new IOException("Writing " + file + " failed"));
    }
}
