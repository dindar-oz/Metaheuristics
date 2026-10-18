package experimentrunner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Command line of the experiment runner: {@code java experimentrunner.Cli [--workspace dir] command ...}, or
 * {@code runner.cmd command ...} in the workspace. Run without a command for the list of commands.
 */
public final class Cli {

    private static final String HELP = """
            Experiment runner. Usage: runner.cmd [--workspace DIR] COMMAND
              submit SPEC.json          add an experiment at the end of the queue (validated first)
              validate SPEC.json        check a spec without submitting it
              list                      the queue with progress
              status [ID]               the worker, or one experiment
              pause ID [--now]          no new runs of ID; --now also stops its runs in flight
              resume ID                 continue ID (also after FAILED or CANCELLED)
              cancel ID                 stop ID for good (its results are kept)
              move ID up|down|top|bottom
              pause-all | resume-all    no new runs for any experiment / again
              threads N                 runs in parallel
              start-worker [--heap 12g] start the background worker (it outlives this command)
              stop-worker [--now]       the worker ends after its runs in flight, or at once
              summary ID                summarize the runs finished so far (summary.md, JOURNAL.md)
              gui                       open the window
              shortcut                  put "Experiment Runner" on the desktop (Windows; opens the window)
              cleanup [--keep N] [--dry-run]
                                        delete frozen code copies in runtime/ except the newest N (default 1)
                                        and the running worker's
            The workspace is DIR, else $EXPERIMENTS_DIR, else ./experiments.
            """;

    private Cli() {
    }

    public static void main(String[] args) {
        List<String> a = new ArrayList<>(Arrays.asList(args));
        // runner.cmd passes its own --workspace first; one given after it wins
        String dir = null;
        for (int w; (w = a.indexOf("--workspace")) >= 0 && w + 1 < a.size(); a.subList(w, w + 2).clear())
            dir = a.get(w + 1);
        Workspace ws = dir != null ? Workspace.open(Path.of(dir)) : Workspace.openDefault();
        WorkerLauncher.writeScripts(ws);
        if (a.isEmpty() || a.get(0).equals("help") || a.get(0).equals("--help")) {
            System.out.print(HELP);
            return;
        }
        try {
            run(new Control(ws), a);
        } catch (RuntimeException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(Control c, List<String> a) {
        String cmd = a.get(0);
        boolean now = a.contains("--now");
        switch (cmd) {
            case "submit" -> {
                ExperimentSpec s = c.submit(Path.of(arg(a, 1)));
                System.out.printf("submitted %s: %d runs; journal %s%n", s.id, s.runs().size(), c.workspace().journalFile(s.id));
                if (!c.workerRunning())
                    System.out.println("the worker is not running: start it with 'start-worker'");
            }
            case "validate" -> {
                ExperimentSpec s = c.validate(Path.of(arg(a, 1)));
                System.out.printf("valid: %s, %d runs (%d configurations x %d instances x %d repeats), %s each%n", s.id,
                        s.runs().size(), s.configs.size(), s.instances.size(), s.seeds.repeats, s.budgetText());
            }
            case "list" -> list(c.workspace());
            case "status" -> {
                if (a.size() > 1)
                    status(c.workspace(), a.get(1));
                else
                    worker(c.workspace());
            }
            case "pause" -> c.pause(arg(a, 1), now);
            case "resume" -> c.resume(arg(a, 1));
            case "cancel" -> c.cancel(arg(a, 1));
            case "move" -> {
                String where = arg(a, 2);
                c.move(arg(a, 1), switch (where) {
                    case "up" -> -1;
                    case "down" -> 1;
                    case "top" -> -1_000_000;
                    case "bottom" -> 1_000_000;
                    default -> throw new IllegalArgumentException("move ID up|down|top|bottom");
                });
            }
            case "pause-all" -> c.pauseAll(true);
            case "resume-all" -> c.pauseAll(false);
            case "threads" -> c.threads(Integer.parseInt(arg(a, 1)));
            case "start-worker" -> {
                int h = a.indexOf("--heap");
                long pid = c.startWorker(h >= 0 ? arg(a, h + 1) : null);
                System.out.println("worker started, pid " + pid + "; log " + c.workspace().logFile());
            }
            case "stop-worker" -> {
                c.stopWorker(now);
                System.out.println(now ? "the worker stops now (its runs in flight run again next time)"
                        : "the worker stops after its runs in flight");
            }
            case "summary" -> {
                Summary s = c.summarize(arg(a, 1));
                System.out.print(s.markdown(c.workspace().spec(arg(a, 1))));
            }
            case "gui" -> experimentrunner.gui.ExperimentRunnerApp.open(c.workspace());
            case "shortcut" -> System.out.println("desktop shortcut: " + WorkerLauncher.desktopShortcut(c.workspace()));
            case "cleanup" -> {
                int k = a.indexOf("--keep");
                WorkerLauncher.cleanup(c.workspace(), k >= 0 ? Integer.parseInt(arg(a, k + 1)) : 1, a.contains("--dry-run"))
                        .forEach(System.out::println);
            }
            default -> throw new IllegalArgumentException("unknown command " + cmd + "; see 'help'");
        }
        if (!cmd.equals("list") && !cmd.equals("status") && !cmd.equals("summary") && !cmd.equals("gui") && !cmd.equals("shortcut") && !cmd.equals("cleanup")
                && !cmd.equals("validate") && !cmd.equals("submit") && !cmd.equals("start-worker") && !cmd.equals("stop-worker"))
            System.out.println("ok");
    }

    private static String arg(List<String> a, int i) {
        if (i >= a.size() || a.get(i).startsWith("--"))
            throw new IllegalArgumentException("missing argument for " + a.get(0) + "; see 'help'");
        return a.get(i);
    }

    private static void list(Workspace ws) {
        worker(ws);
        QueueState q = ws.queue();
        System.out.printf(Locale.ROOT, "%-3s %-34s %-10s %-11s %-8s %s%n", "#", "experiment", "state", "runs", "running", "left");
        int i = 1;
        for (QueueState.Entry e : q.experiments) {
            ExperimentStatus s = ws.status(e.id);
            System.out.printf(Locale.ROOT, "%-3d %-34s %-10s %-11s %-8s %s%s%n", i++, e.id, e.state,
                    s == null ? "-" : s.done + "/" + s.total, s == null ? "-" : Integer.toString(s.running),
                    s == null ? "-" : durationText(s.etaMillis), e.message == null ? "" : "  " + e.message);
        }
    }

    private static void worker(Workspace ws) {
        WorkerInfo w = ws.worker();
        QueueState q = ws.queue();
        long now = System.currentTimeMillis();
        if (ws.workerAlive() && w != null && w.fresh(now))
            System.out.printf("worker: %s, pid %d, %d threads, %d runs in flight, runtime %s%s%n", w.state, w.pid, q.threads,
                    w.running.size(), w.runtime, q.paused ? ", ALL PAUSED" : "");
        else
            System.out.println("worker: not running" + (q.paused ? " (all paused)" : ""));
    }

    private static void status(Workspace ws, String id) {
        QueueState.Entry e = ws.queue().entry(id);
        ExperimentStatus s = ws.status(id);
        ExperimentSpec spec = ws.spec(id);
        System.out.printf("%s: %s%n%s%n", id, spec.title, e.state + (e.message == null ? "" : " (" + e.message + ")"));
        if (s != null)
            System.out.printf("runs %d/%d, %d running, %d failures, mean run %s, left %s%n", s.done, s.total, s.running,
                    s.failures, durationText((long) s.meanRunMillis), durationText(s.etaMillis));
        System.out.println("journal: " + ws.journalFile(id));
        WorkerInfo w = ws.worker();
        if (w != null && w.fresh(System.currentTimeMillis()))
            for (WorkerInfo.Running r : w.running)
                if (r.experiment.equals(id))
                    System.out.printf(Locale.ROOT, "  running %s: %,d iterations, best %.4f, %s%n", r.run, r.iterations,
                            r.bestCost, durationText(System.currentTimeMillis() - r.started));
    }

    public static String durationText(long millis) {
        if (millis < 0)
            return "?";
        long s = millis / 1000;
        return s >= 3600 ? String.format("%dh%02dm", s / 3600, s % 3600 / 60) : s >= 60 ? String.format("%dm%02ds", s / 60, s % 60) : s + "s";
    }
}
