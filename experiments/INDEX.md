# Experiments

Workspace of the experiment runner (package `experimentrunner`). Updated 2026-10-27 07:58.

**Worker:** not running

| # | experiment | title | state | runs | journal |
|---|---|---|---|---|---|
| 1 | `pecto-explorer-tuning-r1` | Explorer parameters on large instances, long runs (round 1) | DONE | 165/165 | [JOURNAL.md](pecto-explorer-tuning-r1/JOURNAL.md) |
| 2 | `pecto-explorer-tuning-r2` | Explorer parameters on large instances, long runs (round 2) | DONE | 150/150 | [JOURNAL.md](pecto-explorer-tuning-r2/JOURNAL.md) |
| 3 | `pecto-hga-vs-aco-1-ga-evals` | Hybrid GA vs PECTOACO, part 1: GA and ablations, equal evaluations | DONE | 200/200 | [JOURNAL.md](pecto-hga-vs-aco-1-ga-evals/JOURNAL.md) |
| 4 | `pecto-hga-vs-aco-2-aco-evals` | Hybrid GA vs PECTOACO, part 2: PECTOACO, equal evaluations | DONE | 50/50 | [JOURNAL.md](pecto-hga-vs-aco-2-aco-evals/JOURNAL.md) |
| 5 | `pecto-hga-vs-aco-3-ga-time` | Hybrid GA vs PECTOACO, part 3: GA, equal time (600 s) | DONE | 50/50 | [JOURNAL.md](pecto-hga-vs-aco-3-ga-time/JOURNAL.md) |
| 6 | `pecto-hga-vs-aco-4-aco-time` | Hybrid GA vs PECTOACO, part 4: PECTOACO, equal time (600 s) | DONE | 50/50 | [JOURNAL.md](pecto-hga-vs-aco-4-aco-time/JOURNAL.md) |
| 7 | `pecto-hga-vs-aco-5-ga-ablation-181k` | Hybrid GA vs PECTOACO, part 5: ablation of every GA mechanism, 181,501 evaluations | DONE | 900/900 | [JOURNAL.md](pecto-hga-vs-aco-5-ga-ablation-181k/JOURNAL.md) |
| 8 | `pecto-hga-vs-aco-6-ga-ablation-1m` | Hybrid GA vs PECTOACO, part 6: ablation of every GA mechanism, 1M evaluations | DONE | 900/900 | [JOURNAL.md](pecto-hga-vs-aco-6-ga-ablation-1m/JOURNAL.md) |

## Picking up after a break (for Claude and people)

1. Read this index. For every experiment that is not CANCELLED, read its `JOURNAL.md`: the question, the design, the **analysis plan** and the **decision rule** were written before any result existed.
2. **DONE** without conclusions: read `summary.md` (and `runs.csv`, `trace.jsonl` for more), apply the analysis plan and the decision rule, and write the Conclusions section of the journal.
3. **QUEUED** or **PAUSED**: the worker resumes it where it stopped. Check the worker line above; if it is not running, start it (`runner.cmd start-worker`, or the GUI).
4. **FAILED**: see the message, `errors.jsonl` and `worker.log`.
5. Commands: `runner.cmd help` in this directory (CLI), `runner.cmd gui` (window).
