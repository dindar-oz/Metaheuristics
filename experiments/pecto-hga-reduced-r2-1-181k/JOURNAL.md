# Reduced hybrid GA, round 2, part 1: 181,501 evaluations

`pecto-hga-reduced-r2-1-181k` · submitted 2026-10-27 08:20

## Question

Is the reduced hybrid GA, which drops every mechanism that round 1 (pecto-hga-vs-aco parts 5 and 6) labeled HARMFUL or REMOVABLE, at least as good as the full GA when the mechanisms are removed together? Removed: the roulette share of selection (tournament only), offspring mutation (and with it the mutation-rate growth), the whole stagnation response (immigration, perturbation, rate growth) and SA on the best individual. Kept: mixed greedy seeding, ARX with cheaper-route preference and best-insertion repair, local search of the offspring, age-protected replacement (inconclusive in round 1, so kept). Secondary: how the reduced GA compares with PECTOACO at 600 s.

## Hypothesis

Round 1 at 1M evaluations, one removal at a time (mean paired gap to the default): tournament only -2.45 %, no mutation -0.17 %, no stagnation response -0.16 %, no SA -0.03 %; at 181,501 evaluations: -0.98 %, -0.01 %, +0.00 %, -0.38 %. If the effects added up, the reduced GA would be about 2.5-3 % better at 1M and about 1.4 % at 181,501. Two risks: (1) interaction: tournament-only selection already ended round 1 with 70 % duplicates, and without the stagnation response nothing re-diversifies the population, so it may collapse; (2) time: without SA the GA ran at about half the evaluations per second (4,514 vs 9,598 in round 1), so at 600 s the reduced GA gets fewer evaluations than the default. Expectation: the reduction is accepted at both evaluation budgets and is better by at least 1 %; at 600 s it is still accepted, by a smaller margin. Against PECTOACO at 600 s the reduced GA narrows the gap on T7 and T10 (round 1 tournament-only was -6.4 % vs the default on T10) but does not close it.

## Design

- **Problem:** Casualty transport (PECTO): instance seed = 42
- **Instances:**
  - `C100` (seedKey 100): instance = custom, ambulances (custom) = 10, patients (custom) = 100, hospitals (custom) = 5, beds per hospital (custom) = 30
  - `T1` (seedKey 1): instance = Table I: 1
  - `T4` (seedKey 4): instance = Table I: 4
  - `T7` (seedKey 7): instance = Table I: 7
  - `T10` (seedKey 10): instance = Table I: 10
- **Algorithm:** Hybrid GA
- **Configurations:**
  - `default` (baseline)
  - `reduced`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 0
- **Seeds:** repeats 0–9, seed = Seeds.derive(20261027, seedKey, repeat), the same in every configuration
- **Budget:** 181,501 evaluations per run
- **Runs:** 100
- **Trace:** every 1000 iterations (trace.jsonl)

## Analysis plan

Three experiments, one per budget: pecto-hga-reduced-r2-1-181k (181,501 evaluations), -2-1m (1,000,000 evaluations), -3-600s (600 s wall time). Two configurations, 'default' (the full research GA) and 'reduced'; the same 5 instances as round 1 (C100; Table I 1, 4, 7, 10; instance seed 42) and NEW seeds (base 20261027, repeats 0-9), so round 1's selection of mechanisms is tested on runs it was not chosen from. Paired by instance and repeat. PRIMARY, per budget over the 50 pairs: paired gap (reduced - default) / default in %, mean with 95 % t-interval, better/worse counts, exact two-sided Wilcoxon signed-rank; per instance (10 pairs) descriptive. SECONDARY: (1) the reduced GA at 600 s against PECTOACO's 600 s runs of pecto-hga-vs-aco-4-aco-time (unpaired: exact Mann-Whitney U per instance, Holm over 5, gap of medians, A12; different seeds are fine for an unpaired comparison); (2) activity at the end (duplicates, diversity, stagnation, evaluations per second, generations), to see whether the population collapses; (3) anytime median curves at 600 s. Analysis script: analyze.py in the folder of pecto-hga-reduced-r2-1-181k, written before any result.

## Decision rule

Per budget: the reduction is ACCEPTED if the upper end of the 95 % interval of the mean paired gap is below +0.1 % (the reduced GA is not worse by 0.1 % or more), and the reduced GA is also BETTER if Wilcoxon p < 0.05 and the mean gap is at most -0.1 %. The reduced GA becomes the recommended default if it is accepted at all three budgets. If it is rejected at a budget, the per-instance gaps and the activity metrics decide which removal to undo first (no further claim without a new round). The PECTOACO comparison is descriptive and does not change the verdict. Conclusions go in the journal of pecto-hga-reduced-r2-1-181k.

## Notes

_Written 2026-10-27 at submission, before any result. This journal is the hub of round 2: the conclusions of all three parts go here._

**Context.** Round 1 is the six-part comparison `pecto-hga-vs-aco`. Its conclusions, in [its hub journal](../pecto-hga-vs-aco-1-ga-evals/JOURNAL.md#conclusions), labeled each GA mechanism at 1M evaluations. Round 2 is the follow-up that round 1's decision rule pre-registered.

**The parts** (configurations `default` and `reduced`, the 5 round-1 instances, seeds base 20261027 repeats 0-9, 7 runs in parallel):

| part | experiment | budget | trace every |
|---|---|---|---|
| 1 | `pecto-hga-reduced-r2-1-181k` | 181,501 evaluations | 1000 generations (final results only) |
| 2 | `pecto-hga-reduced-r2-2-1m` | 1,000,000 evaluations | 250 generations |
| 3 | `pecto-hga-reduced-r2-3-600s` | 600 s wall time | 200 generations |

**`reduced`, as lab parameters of "Hybrid GA":** roulette = 0 (tournament of 5 only), mutation = none (rate 0, no growth), stagnation period = 1,000,000 (the stagnation response never triggers: no migrants, no perturbation, no rate growth), SA inner loop = 0. Everything else keeps its default:
- mixed seeding (half random, a quarter partly greedy, a quarter randomized greedy);
- ARX with cheaper-route preference 0.75 and best-insertion repair, crossover rate 0.75;
- local search of 10 neighbors per offspring;
- minimum victim age 3;
- population 40.

Without SA a generation costs about 82 evaluations instead of about 190.

**Code.** All three parts started on runtime `r-20261026-200519-e72c3f4748` (worker pid 22228, 08:21). That is the same frozen code as round 1's parts 2-6; no source changed in between.

**Analysis script.** `analyze.py` in this folder implements the analysis plan and decision rule, using round 1's tested exact tests. It was written and run on empty and partial data before any result was read. Run it with `python -I -B pecto-hga-reduced-r2-1-181k/analyze.py` from the experiments directory; it writes `analysis.md`.

**What to watch when reading the results.**
- *Population collapse.* In round 1, tournament-only selection already ended with 70 % duplicates, and the reduced GA has nothing that re-diversifies the population. Look at duplicates and stagnation in the activity table.
- *The 600 s part.* Removing SA roughly halves the evaluations per second, so equal time favors the default more than equal evaluations do.

## Results

<!-- results:start (written by the worker; edit outside this block) -->
_Updated 2026-10-27 08:29; also in summary.md._

100 of 100 runs finished.

| configuration | n | mean cost | median | gap to default % | 95% interval | better/worse | p | Holm p | diversity | duplicates | immigrations | mutationRate |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| reduced | 50 | 103764.4612 | 94690.5340 | -1.072 | -1.444 … -0.700 | 39/11 | <0.001 | <0.001 | 0.0003050 | 0.8350 | 0.000 | 0.000 |
| default | 50 | 105642.9447 | 95413.3123 | +0.000 | +0.000 … +0.000 | 0/0 | 1.000 | 1.000 | 0.02906 | 0.2930 | 2.920 | 0.06734 |

Gap to default by instance (%):

| configuration | C100 | T1 | T4 | T7 | T10 |
|---|---|---|---|---|---|
| reduced | -0.087 | +0.071 | -0.662 | -1.575 | -3.107 |
| default | +0.000 | +0.000 | +0.000 | +0.000 | +0.000 |

Gap: (cost − default cost) / default cost of the run with the same instance and repeat; negative is better. p: exact two-sided Wilcoxon signed-rank test of the gaps; Holm p: adjusted over the configurations.
<!-- results:end -->

## Conclusions

_Written 2026-10-27 after all three parts finished (300 of 300 runs); numbers from `analysis.md` (analyze.py)._

**Verdict: the reduced GA is the recommended default.** The decision rule accepted it and found it BETTER at all three budgets:

| budget | mean paired gap (reduced - default) | 95 % interval | better/worse | Wilcoxon p |
|---|---|---|---|---|
| 181,501 evaluations | -1.07 % | -1.44 … -0.70 | 39/11 | <0.001 |
| 1,000,000 evaluations | -2.97 % | -3.84 … -2.10 | 43/7 | <0.001 |
| 600 s | -3.45 % | -4.42 … -2.48 | 40/10 | <0.001 |

**Against the hypothesis.**
- *Additivity held.* The predicted gains were about 1.4 % at 181,501 and 2.5-3 % at 1M; the observed gains were 1.07 % and 2.97 %. Round 1's removals, tested one at a time, add up on new seeds, so its selection of mechanisms was not an artefact of the runs it was chosen from.
- *The time risk did not happen; the prediction was wrong.* At 600 s the reduced GA made only 2.48M evaluations to the default's 5.49M (4,141 vs 9,154 eval/s), but its margin was larger (3.45 %), not smaller. The default's extra evaluations, mostly SA, bought nothing at that budget.
- *The gain grows with instance size and budget.* At 600 s by instance: T10 -8.7 %, T7 -5.6 %, T4 -2.6 %, T1 -0.8 %. The reduced GA won all 10 pairs on each of these instances.

**The collapse risk is real but cheap.** The reduced GA ends with 95-96 % duplicates and zero diversity at every budget (the default: 0-24 %). It keeps improving all the same: median stagnation is 30 generations at 600 s, against 4,360 for the default. In practice it becomes an iterated local search around one solution, with crossover between near-copies. The cost of the collapse shows only on the smallest instance. On C100 the reduced GA reaches 11,228.7 within 15 s and never moves again; it is +0.44 % behind the default at 600 s, losing all 10 pairs. On C100 the default, which keeps diversity, slowly overtakes it.

**Anytime (600 s).** The reduced GA is equal to or slightly worse than the default at 15 s, ahead by 60-120 s, and on T7 and T10 still falling steeply at 600 s (T10 median: 188,096 at 300 s, 181,617 at 600 s). The 600 s budget still limits it on the large instances.

**Against PECTOACO at 600 s (secondary, descriptive).** The reduced GA now beats PECTOACO on C100 (-0.23 %), T1 (-2.12 %) and T4 (-0.51 %; the full GA lost T4 by about 2 %). PECTOACO still wins T7 (+2.33 %) and T10 (+5.49 %). On both instances all 10 PECTOACO runs beat all 10 GA runs (A12 = 0). The gap on T10 fell from about 16 % for the full GA to 5.5 %. As predicted, the reduction narrows the gap on the large instances but does not close it; the instance-dependent picture of round 1 remains, with the crossover point moved toward larger instances.

**Consequences.**
1. Use `reduced` as the GA baseline: roulette 0, no mutation, stagnation response off, SA off. Round 3 (`pecto-hga-components-r3-*`) is already built on it (`R`). It tests every remaining component of R, and SA and LS-depth variants, at 1M evaluations and 300 s, on new instances (instance seed 43).
2. Open question for round 3: SA was REMOVABLE at equal evaluations in round 1. At equal time, however, it is cheap search: it more than doubles the evaluations per second. The `R+SA*` variants at 300 s decide whether SA belongs back in a time-limited GA.
3. *Code defaults changed (2026-10-27, after this round; provisional until round 3 is analysed).* `PECTOHybridGA.builder` and the lab's "Hybrid GA" defaults are now the reduced GA: roulette 0, mutation none, stagnation period 0 (the new "off" value), SA inner loop 0. `PECTOHybridGA.researchBuilder` is the full research GA. A spec's `default` configuration therefore means the full GA only on runtimes frozen before the change, such as every part of rounds 1-3 (`r-20261026-200519-e72c3f4748`). To reproduce the full GA on a newer runtime, set roulette 0.75, mutation costliest route move, stagnation period 100 and SA inner loop 1.
4. Not tested and worth a later round: a cheap way to keep diversity without the harmful stagnation response, such as rejecting duplicate offspring. It targets the C100-type stall and might help the large instances as well.
