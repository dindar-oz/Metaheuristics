/**
 * Experiment runner: a persistent queue of experiments, run by one background {@link experimentrunner.Worker} and
 * controlled from the {@link experimentrunner.Cli} or the GUI ({@code experimentrunner.gui.ExperimentRunnerApp}).
 * <p>
 * Everything lives in a workspace directory (default {@code experiments/}):
 * <pre>
 * queue.json            order and state of the experiments, worker settings; changed under queue.lock only
 * worker.json           heartbeat of the running worker and its runs in flight
 * worker.log            the worker's log
 * INDEX.md              all experiments with their state and journals; the entry point after a break
 * runtime/              frozen copies of the compiled code the worker runs from
 * &lt;id&gt;/spec.json        the experiment as submitted
 * &lt;id&gt;/JOURNAL.md       question, design, analysis plan, decision rule, results, conclusions
 * &lt;id&gt;/runs.jsonl       one line per finished run
 * &lt;id&gt;/trace.jsonl      progress of the runs every trace.every iterations
 * &lt;id&gt;/status.json      progress of the experiment, written by the worker
 * &lt;id&gt;/summary.md       results against the baseline configuration (also summary.json, runs.csv)
 * </pre>
 * A run is identified by (configuration, instance, repeat) and is deterministic: its seed is
 * {@code Seeds.derive(seeds.base, instance.seedKey, repeat)}. So a run that was interrupted (pause now, closing the
 * worker, a reboot) is simply run again, and an experiment resumes where it stopped. Runs are never checkpointed.
 */
package experimentrunner;
