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
- 2026-10-18 12:32: the worker was stopped (runs in flight dropped, they run again) and round 2 paused, to free the machine; resumed at 13:47 at 54/150 runs from runtime r-20261018-134701-162dc2f791. That runtime has the refactored PECTOACO (split into CandidateLists, Pheromone, Ant, AntBuilds; no change to the search). It reproduces the old code bit for bit: the golden-master test (PECTOACOGoldenTest, 12 cases, full per-iteration trace) and identical costs on Table I instance 10. Runs 55-150 are therefore comparable with all earlier runs.

## Results

<!-- results:start (written by the worker; edit outside this block) -->
_Updated 2026-10-18 18:57; also in summary.md._

150 of 150 runs finished.

| configuration | n | mean cost | median | gap to n0 % | 95% interval | better/worse | p | Holm p | branchingFactor | diversity | duplicates | explorerWins |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| n2_r0.05_b0.5_d0.05 | 15 | 153624.7140 | 166901.8581 | -0.209 | -0.357 … -0.061 | 12/3 | 0.0043 | 0.017 | 1.091 | 0.1471 | 0.000 | 0.1333 |
| n4_r0.05_b0.5_d0.05 | 15 | 153642.0501 | 167154.3831 | -0.200 | -0.326 … -0.075 | 13/2 | <0.001 | 0.0027 | 1.098 | 0.1615 | 0.000 | 0.4000 |
| n2_r0.05_b2_d0.05 | 15 | 153654.0159 | 166908.9942 | -0.195 | -0.345 … -0.046 | 13/2 | 0.012 | 0.037 | 1.095 | 0.1473 | 0.000 | 0.8000 |
| n2_r0.1_b0.5_d0.05 | 15 | 153663.8531 | 167139.3700 | -0.187 | -0.314 … -0.060 | 12/3 | 0.0034 | 0.017 | 1.101 | 0.1604 | 0.000 | 0.000 |
| n2_r0.05_b0_d0.05 | 15 | 153673.0540 | 167168.7559 | -0.185 | -0.318 … -0.052 | 13/2 | <0.001 | 0.0034 | 1.077 | 0.1441 | 0.000 | 0.1333 |
| n2_r0.1_b0.5_d0.02 | 15 | 153684.0553 | 167143.5339 | -0.177 | -0.287 … -0.066 | 12/3 | 0.0015 | 0.011 | 1.032 | 0.1392 | 0.000 | 0.000 |
| n2_r0.05_b0.5_d0.02 | 15 | 153753.8678 | 167318.2326 | -0.135 | -0.219 … -0.051 | 11/4 | 0.0026 | 0.016 | 1.018 | 0.1281 | 0.000 | 0.000 |
| n2_r0.02_b0.5_d0.02 | 15 | 153843.6458 | 167747.9899 | -0.080 | -0.180 … +0.019 | 9/6 | 0.151 | 0.151 | 0.9676 | 0.1117 | 0.000 | 1.200 |
| n2_r0.02_b0.5_d0.05 | 15 | 153853.5480 | 167457.1102 | -0.076 | -0.156 … +0.004 | 11/4 | 0.048 | 0.096 | 1.006 | 0.1202 | 0.000 | 1.067 |
| n0 | 15 | 153975.0025 | 167802.6141 | +0.000 | +0.000 … +0.000 | 0/0 | 1.000 | 1.000 | 0.9245 | 0.1109 | 0.000 | |

Gap to n0 by instance (%):

| configuration | T8 | T9 | T10 |
|---|---|---|---|
| n2_r0.05_b0.5_d0.05 | -0.014 | -0.510 | -0.104 |
| n4_r0.05_b0.5_d0.05 | -0.039 | -0.400 | -0.162 |
| n2_r0.05_b2_d0.05 | -0.052 | -0.445 | -0.088 |
| n2_r0.1_b0.5_d0.05 | -0.033 | -0.387 | -0.141 |
| n2_r0.05_b0_d0.05 | -0.061 | -0.397 | -0.096 |
| n2_r0.1_b0.5_d0.02 | -0.049 | -0.371 | -0.110 |
| n2_r0.05_b0.5_d0.02 | -0.040 | -0.289 | -0.075 |
| n2_r0.02_b0.5_d0.02 | -0.030 | -0.151 | -0.061 |
| n2_r0.02_b0.5_d0.05 | -0.042 | -0.154 | -0.032 |
| n0 | +0.000 | +0.000 | +0.000 |

Gap: (cost − n0 cost) / n0 cost of the run with the same instance and repeat; negative is better. p: exact two-sided Wilcoxon signed-rank test of the gaps; Holm p: adjusted over the configurations.
<!-- results:end -->

## Conclusions

_Written 2026-10-18 from runs.csv and trace.jsonl of both rounds, pooled: 30 paired runs per setting (T8, T9, T10 x repeats 0-9)._

**Decision: keep the default `n2_r0.05_b0.5_d0.05`** (2 explorers, rate 0.05, beta 0.5, deposit 0.05).

The setting with the lowest pooled mean gap is `n2_r0.05_b2_d0.05`, at −0.192 %. The default is at −0.185 %. The difference is −0.007 percentage points, 95 % interval [−0.057, +0.042]; it is better in 17 of 30 pairs; exact paired Wilcoxon p = 0.64. The best setting is therefore not significantly better than the default, and the rule keeps the default.

| setting | pooled gap to n0 % | p vs n0 | Δ vs default (pp) | better/worse vs default | p vs default | final diversity |
|---|---|---|---|---|---|---|
| n2_r0.05_b2_d0.05 | −0.192 | 0.0001 | −0.007 | 17/13 | 0.64 | 0.145 |
| **n2_r0.05_b0.5_d0.05 (default)** | **−0.185** | 0.0001 | — | — | — | 0.145 |
| n4_r0.05_b0.5_d0.05 | −0.170 | <0.0001 | +0.014 | 19/11 | 0.98 | 0.162 |
| n2_r0.1_b0.5_d0.05 | −0.167 | 0.0001 | +0.018 | 14/16 | 0.79 | 0.163 |
| n2_r0.05_b0_d0.05 | −0.163 | <0.0001 | +0.022 | 12/18 | 0.64 | 0.141 |
| n2_r0.1_b0.5_d0.02 | −0.135 | <0.0001 | +0.050 | 16/14 | 0.21 | 0.140 |
| n2_r0.02_b0.5_d0.02 | −0.101 | 0.0003 | +0.083 | 13/17 | 0.16 | 0.113 |
| n2_r0.05_b0.5_d0.02 | −0.092 | 0.0010 | +0.092 | 10/20 | **0.017** | 0.127 |
| n2_r0.02_b0.5_d0.05 | −0.079 | 0.0037 | +0.106 | 8/22 | **0.007** | 0.120 |
| n0 | 0 | — | — | — | — | 0.113 |

*Diversity tie-break.* `n2_r0.1_b0.5_d0.05` (0.163) and `n4` (0.162) end with more diversity than the default (0.145). Neither is significantly worse than the default. Read literally, the rule's last sentence ("among statistically equal settings prefer the one with higher final diversity") could pick one of them, but the sentence before it ("if it is not significantly better than the default, keep the default") covers exactly this case. Both have slightly higher mean costs than the default. The default also passes the analysis plan's diversity check: 0.145 against 0.113 for n0. The default is kept. The rule should have said which clause takes precedence; future decision rules should state it.

**What the two rounds show:**

- *Explorers help, and the gain replicated.* In round 2, all 9 explorer settings beat n0 again, 7 of them after Holm. Pooled, the default lowers the cost by 0.185 % (95 % interval [0.10, 0.27] %, p = 0.0001), about 285 cost units on these instances. The effect is real but small.
- *Round 1's leader did not replicate.* In round 2 the order of the top three was default > n4 > b2, against b2 > default > … in round 1. The five settings with deposit 0.05 and rate 0.05-0.1 are practically tied (−0.163 to −0.192 %); round 1's differences were noise.
- *The widening b2 gap was not confirmed.* In round 1 alone, b2's lead over n0 grew until iteration 1500. Pooled, b2 and the default both settle near −0.19 % by iteration 500-1000, and the default is ahead at most checkpoints before 1500.
- *Low rate or low deposit is worse.* Rate 0.02, and deposit 0.02 at rate 0.05, are significantly worse than the default (p = 0.007 and 0.017). This settles the round-1 hypothesis that a smaller deposit would help: it does not.
- *The effect depends on the instance.* Pooled gaps by instance: T8 −0.02 to +0.01 % for all settings, i.e. no effect; T9 about −0.42 % for the top settings; T10 about −0.13 %. This held in both rounds. Why T8 does not benefit is an open question: instance structure (capacity slack, spatial spread of patients) is the first thing to check.

**Comparability.** Runs 55-150 of round 2 ran on the refactored PECTOACO (see Notes). It reproduces the old code bit for bit: the golden-master test, plus a replay of round 1's `n2_r0.05_b0.5_d0.05`/T8/repeat 0 that gave exactly 121841.1721.

**Follow-ups worth considering:** find out why T8 gains nothing; check whether the explorer gain holds at the paper's budgets or on smaller instances; compare tuned PECTOACO with other algorithms (e.g. parallel ruin-and-recreate, parallel SA) under equal evaluations and equal wall time.
