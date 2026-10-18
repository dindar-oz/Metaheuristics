# Explorer parameters on large instances, long runs (round 1)

`pecto-explorer-tuning-r1` · submitted 2026-10-17 16:46

## Question

Which explorer settings (count, rate, heuristic exponent, deposit) give the lowest cost on the large Table I instances when runs are long (1,500 iterations)?

## Hypothesis

The defaults found at 600 iterations (2 explorers, rate 0.05, beta 0.5, deposit 0.05) stay good in long runs; a smaller deposit (0.02) may be better, since larger deposits helped less at 600 iterations.

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
  - `n1_r0.05_b0.5_d0.05`: explorers = 1, explorer rate = 0.05, explorer beta = 0.5, explorer deposit = 0.05
  - `n4_r0.05_b0.5_d0.05`: explorers = 4, explorer rate = 0.05, explorer beta = 0.5, explorer deposit = 0.05
  - `n2_r0.05_b0_d0.05`: explorers = 2, explorer rate = 0.05, explorer beta = 0, explorer deposit = 0.05
  - `n2_r0.05_b2_d0.05`: explorers = 2, explorer rate = 0.05, explorer beta = 2, explorer deposit = 0.05
- **Seeds:** repeats 0–4, seed = Seeds.derive(20261016, seedKey, repeat), the same in every configuration
- **Budget:** 181,501 evaluations per run
- **Runs:** 165
- **Trace:** every 50 iterations (trace.jsonl)

## Analysis plan

Final best cost as the paired gap to the plain colony n0 on the same seeds (same instance and repeat): mean, 95% interval, exact Wilcoxon, Holm over the 10 explorer settings. Secondary: anytime gap curves (trace.jsonl, every 50 iterations) and solution diversity (metric 'diversity': mean share of patients whose predecessor or hospital differs between two ants), used as tie-breakers and as a check that a gain comes with kept diversity. This is round 1 of a two-round race: seeds 0-4, 15 paired runs per setting. Runs 1-31 of this experiment were made by the study script (sens/Tune.java, same code and seeds) and imported; the catalog path was checked to reproduce that script's runs exactly.

## Decision rule

Round 2 (seeds 5-9) gets every setting not clearly beaten: drop a setting if it is worse than the best round-1 setting with a paired Wilcoxon p < 0.05 on the 15 paired runs; always keep the default (n2_r0.05_b0.5_d0.05). After round 2, choose the setting with the lowest mean gap over 30 runs; if it is not significantly better than the default (paired p < 0.05), keep the default. Among statistically equal settings prefer the one with higher final diversity.

## Results

<!-- results:start (written by the worker; edit outside this block) -->
_Updated 2026-10-18 09:00; also in summary.md._

165 of 165 runs finished.

| configuration | n | mean cost | median | gap to n0 % | 95% interval | better/worse | p | Holm p | branchingFactor | diversity | duplicates | explorerWins |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| n2_r0.05_b2_d0.05 | 15 | 153598.9419 | 166874.3325 | -0.189 | -0.311 … -0.066 | 11/4 | 0.0084 | 0.054 | 1.067 | 0.1421 | 0.000 | 0.7333 |
| n2_r0.05_b0.5_d0.05 | 15 | 153647.1643 | 167173.3377 | -0.160 | -0.256 … -0.064 | 11/4 | 0.0067 | 0.054 | 1.065 | 0.1419 | 0.000 | 0.000 |
| n2_r0.1_b0.5_d0.05 | 15 | 153670.6674 | 166950.5855 | -0.146 | -0.253 … -0.039 | 13/2 | 0.0067 | 0.054 | 1.136 | 0.1650 | 0.000 | 0.000 |
| n2_r0.05_b0_d0.05 | 15 | 153680.7276 | 167233.9239 | -0.141 | -0.250 … -0.032 | 10/5 | 0.022 | 0.086 | 1.047 | 0.1385 | 0.000 | 0.000 |
| n4_r0.05_b0.5_d0.05 | 15 | 153687.5355 | 167043.0268 | -0.140 | -0.231 … -0.049 | 13/2 | 0.0012 | 0.012 | 1.089 | 0.1614 | 0.000 | 0.1333 |
| n2_r0.02_b0.5_d0.02 | 15 | 153718.3521 | 167224.7990 | -0.122 | -0.189 … -0.055 | 13/2 | 0.0012 | 0.012 | 0.9969 | 0.1148 | 0.000 | 0.9333 |
| n1_r0.05_b0.5_d0.05 | 15 | 153730.0730 | 167126.2216 | -0.111 | -0.196 … -0.025 | 10/5 | 0.030 | 0.090 | 1.097 | 0.1389 | 0.000 | 0.06667 |
| n2_r0.1_b0.5_d0.02 | 15 | 153759.8538 | 167276.0337 | -0.093 | -0.162 … -0.024 | 13/2 | 0.010 | 0.054 | 1.029 | 0.1416 | 0.000 | 0.000 |
| n2_r0.02_b0.5_d0.05 | 15 | 153782.4420 | 167411.7649 | -0.081 | -0.167 … +0.005 | 11/4 | 0.041 | 0.090 | 0.9956 | 0.1196 | 0.000 | 1.067 |
| n2_r0.05_b0.5_d0.02 | 15 | 153834.2992 | 167704.7554 | -0.049 | -0.108 … +0.009 | 9/6 | 0.135 | 0.135 | 1.008 | 0.1254 | 0.000 | 0.2000 |
| n0 | 15 | 153923.1195 | 167647.9482 | +0.000 | +0.000 … +0.000 | 0/0 | 1.000 | 1.000 | 0.9462 | 0.1150 | 0.000 | |

Gap to n0 by instance (%):

| configuration | T8 | T9 | T10 |
|---|---|---|---|
| n2_r0.05_b2_d0.05 | +0.033 | -0.431 | -0.168 |
| n2_r0.05_b0.5_d0.05 | +0.032 | -0.330 | -0.182 |
| n2_r0.1_b0.5_d0.05 | +0.036 | -0.367 | -0.108 |
| n2_r0.05_b0_d0.05 | +0.023 | -0.315 | -0.132 |
| n4_r0.05_b0.5_d0.05 | -0.006 | -0.289 | -0.125 |
| n2_r0.02_b0.5_d0.02 | -0.009 | -0.223 | -0.133 |
| n1_r0.05_b0.5_d0.05 | +0.041 | -0.284 | -0.089 |
| n2_r0.1_b0.5_d0.02 | +0.035 | -0.188 | -0.125 |
| n2_r0.02_b0.5_d0.05 | +0.018 | -0.152 | -0.109 |
| n2_r0.05_b0.5_d0.02 | +0.022 | +0.000 | -0.171 |
| n0 | +0.000 | +0.000 | +0.000 |

Gap: (cost − n0 cost) / n0 cost of the run with the same instance and repeat; negative is better. p: exact two-sided Wilcoxon signed-rank test of the gaps; Holm p: adjusted over the configurations.
<!-- results:end -->

## Conclusions

_Written 2026-10-18 from runs.csv and trace.jsonl._

**Round-1 decision (per the rule).** The best round-1 setting is `n2_r0.05_b2_d0.05` (mean gap −0.189 %). Each other setting was then tested against it with an exact paired Wilcoxon test on its 15 paired gaps:

| setting | mean gap % | Δ vs best (pp) | worse than best in | p vs best | round 2 |
|---|---|---|---|---|---|
| n2_r0.05_b2_d0.05 | −0.189 | — | — | — | keep (best) |
| n2_r0.05_b0.5_d0.05 (default) | −0.160 | +0.029 | 10/15 | 0.30 | keep (default) |
| n2_r0.1_b0.5_d0.05 | −0.146 | +0.043 | 11/15 | 0.12 | keep |
| n2_r0.05_b0_d0.05 | −0.141 | +0.048 | 9/15 | 0.45 | keep |
| n4_r0.05_b0.5_d0.05 | −0.140 | +0.049 | 10/15 | 0.15 | keep |
| n2_r0.02_b0.5_d0.02 | −0.122 | +0.067 | 9/15 | 0.23 | keep |
| n1_r0.05_b0.5_d0.05 | −0.111 | +0.078 | 12/15 | **0.041** | **drop** |
| n2_r0.1_b0.5_d0.02 | −0.093 | +0.096 | 11/15 | 0.083 | keep |
| n2_r0.02_b0.5_d0.05 | −0.081 | +0.108 | 10/15 | 0.073 | keep |
| n2_r0.05_b0.5_d0.02 | −0.049 | +0.139 | 10/15 | 0.064 | keep |

Round 2 (seeds 5–9) therefore runs 9 explorer settings plus `n0`. `n0` is not a candidate, but it has to be rerun because every gap is measured against it. Only `n1` is dropped. The rule has no multiplicity correction, so three more settings (p 0.06–0.08) only just stay in. Round 2 will most likely decide between the top five.

**What round 1 says, provisionally:**

- *Explorers help, but only a little.* All 10 explorer settings have a lower mean cost than `n0`, by 0.05–0.19 %. Against n0 only `n4` and `n2_r0.02_b0.5_d0.02` stay significant after Holm (p = 0.012). The default and b2 reach Holm p = 0.054.
- *The gain comes from T9 and T10. T8 shows none:* on T8 every setting is within ±0.04 % of n0, mostly slightly worse. Round 2 should report the per-instance gaps again. If T8 stays flat, that points to a size or structure effect, not noise.
- *Hypothesis, part 1 (the defaults stay good in long runs): supported so far.* The default is second and cannot be told apart from the best (p = 0.30).
- *Hypothesis, part 2 (deposit 0.02 is better): not supported.* At rate 0.05 and rate 0.1, deposit 0.02 is clearly worse than 0.05 (−0.049 vs −0.160 and −0.093 vs −0.146). Only at rate 0.02 does 0.02 do better (−0.122 vs −0.081).
- *The b2 lead grows over time; the default's gain is flat.* Anytime gap of b2 to n0: −0.13 % at iteration 100, −0.08 % at 250, −0.14 % at 750, −0.17 % at 1000, −0.19 % at 1500. The default holds about −0.15 to −0.16 % from iteration 100 onward. b2 and `n2_r0.1_b0.5_d0.05` also improve the most between iterations 1000 and 1500 (0.046 % vs 0.027 % for n0). This is the one trend that may matter: if runs get longer, b2's lead could grow. Round 2 should check whether the trend repeats.
- *Diversity.* Settings with deposit 0.05 keep final diversity at 0.14–0.165, against 0.115 for n0. Rate 0.1 and n4 keep the most. The gains therefore come with kept diversity, as the analysis plan required. The exception is `n2_r0.02_b0.5_d0.02`: it ends at n0's diversity (0.115) and still gains 0.12 %. It also has many explorer wins (0.93 per run), so its gain probably comes from explorer solutions directly, not from extra diversity.
- *Explorer wins* (explorerWins: how often an explorer ant found the new best) are close to zero for most settings. The high counts belong to the low-rate settings (r0.02: 0.93 and 1.07 per run) and to b2 (0.73). For the default, rate 0.1 and b0 the count is 0, yet they still gain. In those settings the explorers seem to work only through their pheromone deposits, by keeping alternatives open, not by finding the best solution themselves.

**Next:** round 2 submitted 2026-10-18 as `pecto-explorer-tuning-r2` (same worker runtime r-20261017-180902-0e9417ca55 as round 1): same instances and budget, seeds 5–9, the 9 kept settings plus n0. Then apply the second half of the decision rule to all 30 paired runs.
