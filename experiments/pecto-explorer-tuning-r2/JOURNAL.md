# Explorer parameters on large instances, long runs (round 2)

`pecto-explorer-tuning-r2` · submitted 2026-10-18 09:01

## Question

Which explorer settings (count, rate, heuristic exponent, deposit) give the lowest cost on the large Table I instances when runs are long (1,500 iterations)?

## Hypothesis

From round 1: the default (n2_r0.05_b0.5_d0.05) stays among the best and is hard to beat; n2_r0.05_b2_d0.05 led round 1 (-0.189% vs -0.160%) with a gap that kept widening through the run, so it is the most likely to beat the default; deposit 0.02 does not help except possibly at rate 0.02; the gain is on T9/T10, not on T8.

## Design

- **Problem:** Casualty transport (PECTO): instance seed = 42
- **Instances:**
  - `T8` (seedKey 8): instance = Table I: 8
  - `T9` (seedKey 9): instance = Table I: 9
  - `T10` (seedKey 10): instance = Table I: 10
- **Algorithm:** ACO (MMAS)
- **Configurations:**
  - `n0` (baseline): explorers = 0, explorer rate = 0.05, explorer beta = 0.5, explorer deposit = 0.05
  - `n2_r0.02_b0.5_d0.02`: explorers = 2, explorer rate = 0.02, explorer beta = 0.5, explorer deposit = 0.02
  - `n2_r0.05_b0.5_d0.02`: explorers = 2, explorer rate = 0.05, explorer beta = 0.5, explorer deposit = 0.02
  - `n2_r0.1_b0.5_d0.02`: explorers = 2, explorer rate = 0.1, explorer beta = 0.5, explorer deposit = 0.02
  - `n2_r0.02_b0.5_d0.05`: explorers = 2, explorer rate = 0.02, explorer beta = 0.5, explorer deposit = 0.05
  - `n2_r0.05_b0.5_d0.05`: explorers = 2, explorer rate = 0.05, explorer beta = 0.5, explorer deposit = 0.05
  - `n2_r0.1_b0.5_d0.05`: explorers = 2, explorer rate = 0.1, explorer beta = 0.5, explorer deposit = 0.05
  - `n4_r0.05_b0.5_d0.05`: explorers = 4, explorer rate = 0.05, explorer beta = 0.5, explorer deposit = 0.05
  - `n2_r0.05_b0_d0.05`: explorers = 2, explorer rate = 0.05, explorer beta = 0, explorer deposit = 0.05
  - `n2_r0.05_b2_d0.05`: explorers = 2, explorer rate = 0.05, explorer beta = 2, explorer deposit = 0.05
- **Seeds:** repeats 5–9, seed = Seeds.derive(20261016, seedKey, repeat), the same in every configuration
- **Budget:** 181,501 evaluations per run
- **Runs:** 150
- **Trace:** every 50 iterations (trace.jsonl)

## Analysis plan

Round 2 of the two-round race begun in pecto-explorer-tuning-r1: same instances, budget and code, seeds 5-9 (15 new paired runs per setting). n1_r0.05_b0.5_d0.05 was dropped after round 1 (worse than the best round-1 setting, paired Wilcoxon p = 0.041). n0 is rerun because it is the reference of every gap. Final best cost as the paired gap to n0 on the same instance and repeat. The decision uses rounds 1+2 pooled (30 paired runs per setting): mean gap, 95% interval, exact paired Wilcoxon; this round's summary alone is a replication check (same ranking? same per-instance pattern, T8 flat?). Secondary: anytime gap curves (does the b2 gap keep widening late?) and final diversity, as tie-breakers.

## Decision rule

Pool rounds 1 and 2 (30 paired runs per setting). Choose the setting with the lowest mean gap to n0; if it is not significantly better than the default n2_r0.05_b0.5_d0.05 (exact paired Wilcoxon on the 30 per-run gap differences, p < 0.05), keep the default. Among statistically equal settings prefer the one with higher final diversity.

## Notes

- 2026-10-18 09:26: the worker was stopped gracefully after 7 runs, so that it would pick up a tray fix, and restarted at 09:30 from runtime r-20261018-092959-c1cc39c193 (no runs lost). Runs 1-7 used r-20261017-180902-0e9417ca55, the same runtime as round 1. The two runtimes differ only in runner classes (experimentrunner.Cli, WorkerLauncher, experimentrunner.gui.*). Every algorithm and problem class is byte-identical, so all runs of both rounds stay comparable.

## Results

<!-- results:start (written by the worker; edit outside this block) -->
No runs finished yet.
<!-- results:end -->

## Conclusions

_Not written yet: read the results against the analysis plan and the decision rule, then write here what was decided and why._
