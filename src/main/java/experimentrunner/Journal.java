package experimentrunner;

import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The human-readable side of the workspace: JOURNAL.md of every experiment (why it runs, how it is designed, how its
 * results will be read, the results, the conclusions) and INDEX.md over all of them. The worker only rewrites the
 * block between the results markers of a journal; everything else in it is for people (and Claude) to edit.
 */
public final class Journal {

    static final String RESULTS_START = "<!-- results:start (written by the worker; edit outside this block) -->";
    static final String RESULTS_END = "<!-- results:end -->";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private Journal() {
    }

    static String time(long millis) {
        return TIME.format(Instant.ofEpochMilli(millis));
    }

    // a new journal, written when the experiment is submitted
    static void create(Workspace ws, ExperimentSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(spec.title).append("\n\n");
        sb.append("`").append(spec.id).append("` · submitted ").append(time(System.currentTimeMillis())).append("\n\n");
        section(sb, "Question", spec.question);
        section(sb, "Hypothesis", spec.hypothesis);
        sb.append("## Design\n\n");
        sb.append("- **Problem:** ").append(spec.problem).append(params(spec.problemParams)).append('\n');
        sb.append("- **Instances:**\n");
        for (ExperimentSpec.Instance in : spec.instances)
            sb.append("  - `").append(in.label).append("` (seedKey ").append(in.seedKey).append(')').append(params(in.params)).append('\n');
        sb.append("- **Algorithm:** ").append(spec.algorithm).append(params(spec.algorithmParams)).append('\n');
        sb.append("- **Configurations:**\n");
        for (ExperimentSpec.Config c : spec.configs)
            sb.append("  - `").append(c.label).append('`').append(c.label.equals(spec.baseline) ? " (baseline)" : "")
                    .append(params(c.params)).append('\n');
        sb.append("- **Seeds:** repeats ").append(spec.seeds.firstRepeat).append('–')
                .append(spec.seeds.firstRepeat + spec.seeds.repeats - 1).append(", seed = Seeds.derive(")
                .append(spec.seeds.base).append(", seedKey, repeat), the same in every configuration\n");
        sb.append("- **Budget:** ").append(spec.budgetText()).append(" per run\n");
        sb.append("- **Runs:** ").append(spec.runs().size()).append('\n');
        if (spec.traceEvery > 0)
            sb.append("- **Trace:** every ").append(spec.traceEvery).append(" iterations (trace.jsonl)\n");
        sb.append('\n');
        section(sb, "Analysis plan", spec.analysisPlan);
        section(sb, "Decision rule", spec.decisionRule);
        sb.append("## Results\n\n").append(RESULTS_START).append("\nNo runs finished yet.\n").append(RESULTS_END).append("\n\n");
        sb.append("## Conclusions\n\n_Not written yet: read the results against the analysis plan and the decision rule, "
                + "then write here what was decided and why._\n");
        Json.writeString(ws.journalFile(spec.id), sb.toString());
    }

    private static void section(StringBuilder sb, String title, String text) {
        sb.append("## ").append(title).append("\n\n")
                .append(text == null || text.isBlank() ? "_Not given._" : text.strip()).append("\n\n");
    }

    private static String params(Map<String, Object> params) {
        if (params == null || params.isEmpty())
            return "";
        return ": " + params.entrySet().stream().map(e -> e.getKey() + " = " + value(e.getValue()))
                .collect(Collectors.joining(", "));
    }

    private static String value(Object v) {
        if (v instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15)
            return Long.toString(d.longValue());
        return String.valueOf(v);
    }

    // replaces the results block of the journal (the rest stays as people edited it)
    static void writeResults(Workspace ws, String id, String markdown) {
        String journal = Summary.read(ws.journalFile(id));
        int start = journal.indexOf(RESULTS_START), end = journal.indexOf(RESULTS_END);
        String block = RESULTS_START + "\n_Updated " + time(System.currentTimeMillis()) + "; also in summary.md._\n\n"
                + markdown.strip() + "\n" + RESULTS_END;
        String updated = start >= 0 && end > start
                ? journal.substring(0, start) + block + journal.substring(end + RESULTS_END.length())
                : journal + "\n## Results\n\n" + block + "\n";
        Json.writeString(ws.journalFile(id), updated);
    }

    // INDEX.md: every experiment with its state, progress and journal, and how to pick up the work after a break
    static void writeIndex(Workspace ws, QueueState q) {
        StringBuilder sb = new StringBuilder("# Experiments\n\n");
        sb.append("Workspace of the experiment runner (package `experimentrunner`). Updated ")
                .append(time(System.currentTimeMillis())).append(".\n\n");
        WorkerInfo w = ws.worker();
        long now = System.currentTimeMillis();
        sb.append("**Worker:** ").append(w != null && w.fresh(now)
                ? "running (pid " + w.pid + ", " + w.threads + " threads, since " + time(w.started) + ")"
                : "not running").append(q.paused ? " · **all paused**" : "").append("\n\n");
        sb.append("| # | experiment | title | state | runs | journal |\n|---|---|---|---|---|---|\n");
        int i = 1;
        for (QueueState.Entry e : q.experiments) {
            ExperimentStatus s = ws.status(e.id);
            String title;
            try {
                title = ws.spec(e.id).title;
            } catch (RuntimeException ex) {
                title = "?";
            }
            sb.append(String.format(Locale.ROOT, "| %d | `%s` | %s | %s%s | %s | [JOURNAL.md](%s/JOURNAL.md) |%n", i++, e.id,
                    title.replace("|", "/"), e.state, e.message == null ? "" : " (" + e.message.replace("|", "/") + ")",
                    s == null ? "–" : s.done + "/" + s.total, e.id));
        }
        sb.append("""

                ## Picking up after a break (for Claude and people)

                1. Read this index. For every experiment that is not CANCELLED, read its `JOURNAL.md`: the question, \
                the design, the **analysis plan** and the **decision rule** were written before any result existed.
                2. **DONE** without conclusions: read `summary.md` (and `runs.csv`, `trace.jsonl` for more), apply the \
                analysis plan and the decision rule, and write the Conclusions section of the journal.
                3. **QUEUED** or **PAUSED**: the worker resumes it where it stopped. Check the worker line above; if it \
                is not running, start it (`runner.cmd start-worker`, or the GUI).
                4. **FAILED**: see the message, `errors.jsonl` and `worker.log`.
                5. Commands: `runner.cmd help` in this directory (CLI), `runner.cmd gui` (window).
                """);
        try {
            Json.writeString(ws.indexFile(), sb.toString());
        } catch (RuntimeException ignored) {
            // the index is a convenience; it is written again on the next change
        }
    }

    static boolean exists(Workspace ws, String id) {
        return Files.exists(ws.journalFile(id));
    }
}
