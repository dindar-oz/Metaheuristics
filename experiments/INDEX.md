# Experiments

Workspace of the experiment runner (package `experimentrunner`). Updated 2026-10-18 09:37.

**Worker:** running (pid 30484, 7 threads, since 2026-10-18 09:30)

| # | experiment | title | state | runs | journal |
|---|---|---|---|---|---|
| 1 | `pecto-explorer-tuning-r1` | Explorer parameters on large instances, long runs (round 1) | DONE | 165/165 | [JOURNAL.md](pecto-explorer-tuning-r1/JOURNAL.md) |
| 2 | `pecto-explorer-tuning-r2` | Explorer parameters on large instances, long runs (round 2) | QUEUED | 7/150 | [JOURNAL.md](pecto-explorer-tuning-r2/JOURNAL.md) |

## Picking up after a break (for Claude and people)

1. Read this index. For every experiment that is not CANCELLED, read its `JOURNAL.md`: the question, the design, the **analysis plan** and the **decision rule** were written before any result existed.
2. **DONE** without conclusions: read `summary.md` (and `runs.csv`, `trace.jsonl` for more), apply the analysis plan and the decision rule, and write the Conclusions section of the journal.
3. **QUEUED** or **PAUSED**: the worker resumes it where it stopped. Check the worker line above; if it is not running, start it (`runner.cmd start-worker`, or the GUI).
4. **FAILED**: see the message, `errors.jsonl` and `worker.log`.
5. Commands: `runner.cmd help` in this directory (CLI), `runner.cmd gui` (window).
