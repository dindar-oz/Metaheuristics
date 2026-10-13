package experiments;

import metaheuristic.run.RunResult;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes one CSV row per run, including its seed and the full algorithm description, so any run can be reproduced.
 */
public class CsvERH implements ExperimentResultHandler {
    private final Path file;
    private PrintWriter out;

    public CsvERH(Path file) {
        this.file = file;
    }

    @Override
    public void begin(long baseSeed) {
        try {
            if (file.getParent() != null)
                Files.createDirectories(file.getParent());
            out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.println("case,repeat,seed,bestCost,evaluations,iterations,elapsedMs,timeToBestMs,stopReason,algorithm,problem");
    }

    @Override
    public void handle(ExperimentCase<?, ?> experimentCase, List<RunResult<?>> runs) {
        for (int r = 0; r < runs.size(); r++) {
            RunResult<?> run = runs.get(r);
            out.printf("%s,%d,%d,%s,%d,%d,%d,%d,%s,%s,%s%n", quote(experimentCase.getTitle()), r, run.seed(),
                    run.bestCost(), run.evaluations(), run.iterations(), run.elapsedMillis(), run.timeToBestMillis(),
                    run.stopReason(), quote(run.info().algorithm()), quote(run.info().problem()));
        }
        out.flush();
        if (out.checkError())
            throw new UncheckedIOException(new IOException("Writing " + file + " failed"));
    }

    @Override
    public void end() {
        out.close();
    }

    private static String quote(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
