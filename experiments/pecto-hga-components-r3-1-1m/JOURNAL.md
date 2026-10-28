# Hybrid GA components, round 3, part 1: 1M evaluations

`pecto-hga-components-r3-1-1m` · submitted 2026-10-27 08:39

## Question

Which components of the hybrid GA are conclusively worth keeping, and which are not? Round 1 (pecto-hga-vs-aco parts 5-6) removed one mechanism at a time from the full GA, at equal evaluations, on 5 instances generated with instance seed 42, with SA and local-search settings chosen without tuning. Round 3 checks every verdict against the four reasons it could be wrong: interactions (it ablates around the reduced GA, removing each kept part and adding back each dropped part), budget type (1M evaluations and 300 s), settings (three SA schedules, three local-search budgets) and generalization (new instances, instance seed 43, and new run seeds).

## Hypothesis

From round 1 at 1M evaluations (gap of removing the component from the full GA): seeding +10.2 %, best-insertion repair +11.6 %, recombination +4.7 %, cheaper-route bias +1.5 %, local search +0.27 %, tournament-only -2.45 % vs the mix, mutation -0.17 %, stagnation response -0.16 %, SA -0.03 %, age protection +0.06 % (inconclusive). Expected in round 3: seeding, repair, recombination and tournament selection are essential around R too and on the new instances; the cheaper-route bias stays essential on the large instances; local search stays useful with some budget (not necessarily 10); adding back mutation, the stagnation response or SA does not help at 1M evaluations with any of the tested SA schedules; at 300 s, SA's cheap evaluations may make R+SAlight or R+SA competitive (equal-time effect); age protection stays inconclusive.

## Design

- **Problem:** Casualty transport (PECTO): instance seed = 43
- **Instances:**
  - `C100` (seedKey 100): instance = custom, ambulances (custom) = 10, patients (custom) = 100, hospitals (custom) = 5, beds per hospital (custom) = 30
  - `T1` (seedKey 1): instance = Table I: 1
  - `T4` (seedKey 4): instance = Table I: 4
  - `T7` (seedKey 7): instance = Table I: 7
  - `T10` (seedKey 10): instance = Table I: 10
- **Algorithm:** Hybrid GA
- **Configurations:**
  - `reduced` (baseline): roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 0
  - `full`
  - `R-seeding`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 0, seeding = random
  - `R-repair`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 0, ARX repair = random
  - `R-cheaperBias`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 0, cheaper route = 0.5
  - `R-recombination`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 0, crossover = none
  - `R-LS`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 0, local search steps = 0
  - `R-ageing`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 0, min victim age = 0
  - `R-tournament`: roulette = 0.75, mutation = none, stagnation period = 1000000, SA inner loop = 0
  - `R+mutation`: roulette = 0, stagnation period = 1000000, SA inner loop = 0
  - `R+stagnation`: roulette = 0, mutation = none, SA inner loop = 0
  - `R+SA`: roulette = 0, mutation = none, stagnation period = 1000000
  - `R+SAlight`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 1, SA t0 = 5, SA t min = 0.5
  - `R+SAstrong`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 5
  - `R+LS3`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 0, local search steps = 3
  - `R+LS30`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 0, local search steps = 30
- **Seeds:** repeats 0–9, seed = Seeds.derive(20261028, seedKey, repeat), the same in every configuration
- **Budget:** 1,000,000 evaluations per run
- **Runs:** 800
- **Trace:** every 1000 iterations (trace.jsonl)

## Analysis plan

Two experiments: pecto-hga-components-r3-1-1m (16 configurations, 1,000,000 evaluations) and pecto-hga-components-r3-2-300s (the 8 configurations whose evaluations cost differently: reduced, full, R-LS, the three SA variants, R+LS3, R+LS30; 300 s wall time). Instances: C100 (custom 10 ambulances / 100 patients / 5 hospitals x 30 beds) and Table I 1, 4, 7, 10, all generated with instance seed 43 (new instances, same sizes as rounds 1-2); run seeds base 20261028, repeats 0-9. Baseline 'reduced' (R): mixed greedy seeding, ARX (cheaper-route 0.75, best insertion), local search 10 steps, tournament-only selection, no mutation, no stagnation response, no SA, age protection 3. PRIMARY, per budget and variant over the 50 pairs (same instance and repeat): paired gap (variant - R) / R in %, mean with 95 % t-interval, better/worse, exact Wilcoxon signed-rank, Holm over the variants of that budget (15 at 1M, 7 at 300 s); per instance descriptive. The final classification combines round 1 (part 6, computed from its data) and round 3, per component. SECONDARY: full vs reduced (replication of round 2 on new instances); setting sensitivity of SA and local search; activity at the end (duplicates, diversity, evaluations per second, generations). Analysis script: analyze.py in the folder of pecto-hga-components-r3-1-1m, written before any result.

## Decision rule

Per budget, for a KEPT component (variant R-x): it is NEEDED if R-x is significantly worse (Holm p < 0.05, mean gap >= +0.1 %); NOT NEEDED if the upper end of the 95 % interval of the mean gap is below +0.1 %; else UNDECIDED. For a DROPPED component (R+x): it HELPS if R+x is significantly better (Holm p < 0.05, mean gap <= -0.1 %); it DOES NOT HELP if the lower end of the 95 % interval is above -0.1 % (adding it gains less than 0.1 %); else UNDECIDED. SA and local search are judged on their best tested setting: SA helps if any of R+SA, R+SAlight, R+SAstrong helps; local search is needed if R-LS is worse than R (and its setting sensitivity is reported from R+LS3 / R+LS30). FINAL, per component, decided by round 3 (new data, and the reduced GA is the design being kept): WORTH KEEPING if NEEDED (or, for a dropped component, HELPS) at every budget it was tested at; NOT WORTH KEEPING if NOT NEEDED / DOES NOT HELP at every tested budget; BUDGET-DEPENDENT if it is worth keeping at one budget and not at the other; otherwise UNDECIDED. Round 1 is reported beside it: CONFIRMED if round 1 points the same way, CONTRADICTS ROUND 1 otherwise (an interaction with the removed mechanisms or an instance effect), and NEW if round 1 was inconclusive. The 1M verdict covers every component; the 300 s verdict only those tested at 300 s. Conclusions go in the journal of pecto-hga-components-r3-1-1m.

## Notes

_Written 2026-10-27 at submission, before any result. This journal is the hub of round 3: the conclusions go here._

**Context.** The user asked for a conclusive answer on which hybrid GA components are worth keeping.
- *Round 1* ([pecto-hga-vs-aco hub](../pecto-hga-vs-aco-1-ga-evals/JOURNAL.md#conclusions)) removed one mechanism at a time from the full GA.
- *Round 2* ([pecto-hga-reduced-r2 hub](../pecto-hga-reduced-r2-1-181k/JOURNAL.md)) tests removing all weak mechanisms together.
- *Round 3* checks each component against the four ways round 1 could be wrong: interactions, budget type, settings, and the instances it was chosen on.

When round 3 was submitted, round 2's 181k part was done and its other parts were running. Round 2's results had not been read; round 3's design does not depend on them.

**The parts** (instance seed 43 = new instances of the same sizes as rounds 1-2; seeds base 20261028, repeats 0-9; 7 runs in parallel):

| part | experiment | configurations | budget |
|---|---|---|---|
| 1 | `pecto-hga-components-r3-1-1m` | all 16 | 1,000,000 evaluations |
| 2 | `pecto-hga-components-r3-2-300s` | reduced, full, R-LS, R+SA, R+SAlight, R+SAstrong, R+LS3, R+LS30 | 300 s wall time |

Part 2 is limited to the configurations whose evaluations cost differently from R's; for the others the budget type cannot change the verdict much. It uses 300 s rather than 600 s to keep it to about 5 hours.

**Configurations** (lab parameters of "Hybrid GA"; R = reduced = roulette 0, mutation none, stagnation period 1,000,000, SA inner loop 0):

| label | change from R | tests |
|---|---|---|
| reduced | — | baseline |
| full | the research defaults | round 2 on new instances (gap negative = full better) |
| R-seeding | seeding = random | greedy-based seeding |
| R-repair | ARX repair = random | best-insertion repair |
| R-cheaperBias | cheaper route = 0.5 | cheaper-route preference |
| R-recombination | crossover = none (clone) | recombination |
| R-LS | local search steps = 0 | local search (10 steps) |
| R-ageing | min victim age = 0 | age protection |
| R-tournament | roulette = 0.75 | tournament-only selection (positive gap = tournament-only is needed) |
| R+mutation | CostliestRouteMove at a fixed 0.05 (no stagnation, so no growth) | offspring mutation |
| R+stagnation | stagnation period = 100 (migrants + perturbation; mutation stays off, so no rate growth) | stagnation response |
| R+SA | SA 25 → 0.1, ×0.95, 1 neighbor per temperature (round 1's setting, 108 evaluations per generation) | SA |
| R+SAlight | SA 5 → 0.5, ×0.95, 1 neighbor (about 45 evaluations per generation) | a cheaper SA |
| R+SAstrong | SA 25 → 0.1, ×0.95, 5 neighbors (about 540 evaluations per generation) | a stronger SA |
| R+LS3 / R+LS30 | local search steps 3 / 30 (R has 10) | local-search budget sensitivity |

**Code.** Runtime `r-20261026-200519-e72c3f4748` (worker pid 22228, the same frozen code as rounds 1 parts 2-6 and round 2).

**Decision rule revised before submission.** As first written, the FINAL verdict needed round 1 and round 3 to agree. A test of `analyze.py` on synthetic data showed two problems: a component that round 1 left inconclusive (age protection) could never be decided, and a real interaction (a component that helps only in the reduced GA) would only show as a disagreement. The rule was changed before any round-3 data existed:
- *Round 3 decides.* It has new data and tests the design actually being kept.
- *Round 1 is reported beside it* as CONFIRMED, CONTRADICTS ROUND 1, or NEW.
- *BUDGET-DEPENDENT* was added for a component that is worth keeping at one budget only.
- *Record:* the spec in this folder and `analyze.py` hold the revised rule; the first version is in no file.

**Analysis script.** `analyze.py` in this folder implements the plan and the revised rule. It recomputes round 1's verdicts from `pecto-hga-vs-aco-6-ga-ablation-1m/runs.jsonl`, and it reproduced them exactly. It was tested on empty data and on synthetic data with known answers before submission. Run it with `python -I -B pecto-hga-components-r3-1-1m/analyze.py` from the experiments directory; it writes `analysis.md`.

## Results

<!-- results:start (written by the worker; edit outside this block) -->
_Updated 2026-10-28 09:10; also in summary.md._

800 of 800 runs finished.

| configuration | n | mean cost | median | gap to reduced % | 95% interval | better/worse | p | Holm p | diversity | duplicates | immigrations | mutationRate |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| R+LS3 | 50 | 99062.1553 | 92174.9246 | -0.183 | -0.397 … +0.031 | 24/26 | 0.660 | 0.660 | 3.440e-05 | 0.9555 | 0.000 | 0.000 |
| R+stagnation | 50 | 99521.3066 | 91868.4495 | -0.044 | -0.082 … -0.007 | 27/12 | 0.0052 | 0.026 | 0.0006097 | 0.8925 | 36.66 | 0.000 |
| reduced | 50 | 99548.4469 | 92011.8952 | +0.000 | +0.000 … +0.000 | 0/0 | 1.000 | 1.000 | 0.0001971 | 0.9030 | 0.000 | 0.000 |
| R+mutation | 50 | 99693.5157 | 92029.7376 | +0.073 | -0.054 … +0.200 | 18/32 | 0.101 | 0.303 | 0.0004148 | 0.8700 | 0.000 | 0.05000 |
| R-cheaperBias | 50 | 99685.8623 | 92099.6683 | +0.084 | -0.038 … +0.207 | 22/28 | 0.101 | 0.303 | 0.0001771 | 0.8950 | 0.000 | 0.000 |
| R+SAlight | 50 | 100366.0071 | 92053.2492 | +0.398 | +0.171 … +0.626 | 17/33 | 0.0014 | 0.0086 | 0.0002170 | 0.8655 | 0.000 | 0.000 |
| R-ageing | 50 | 100249.1761 | 92217.7157 | +0.406 | +0.237 … +0.575 | 11/39 | <0.001 | <0.001 | 0.0002274 | 0.8780 | 0.000 | 0.000 |
| R+LS30 | 50 | 101161.1521 | 91775.4730 | +0.740 | +0.282 … +1.198 | 23/27 | 0.055 | 0.222 | 0.0004052 | 0.7900 | 0.000 | 0.000 |
| R+SA | 50 | 101480.3845 | 92070.0806 | +0.947 | +0.455 … +1.438 | 17/33 | <0.001 | 0.0014 | 0.0002877 | 0.8470 | 0.000 | 0.000 |
| R+SAstrong | 50 | 104025.4506 | 92865.2325 | +2.357 | +1.367 … +3.346 | 7/43 | <0.001 | <0.001 | 0.002024 | 0.6225 | 0.000 | 0.000 |
| R-recombination | 50 | 103944.4944 | 94771.2513 | +2.922 | +2.276 … +3.568 | 9/41 | <0.001 | <0.001 | 0.0001533 | 0.9370 | 0.000 | 0.000 |
| R-tournament | 50 | 105895.7019 | 93004.7550 | +3.415 | +2.031 … +4.800 | 4/46 | <0.001 | <0.001 | 0.0008992 | 0.7685 | 0.000 | 0.000 |
| full | 50 | 106885.4193 | 93428.0962 | +3.975 | +2.406 … +5.543 | 6/44 | <0.001 | <0.001 | 0.03583 | 0.06400 | 30.88 | 0.5764 |
| R-LS | 50 | 112666.2265 | 95446.4115 | +8.142 | +5.608 … +10.675 | 0/50 | <0.001 | <0.001 | 0.000 | 0.9750 | 0.000 | 0.000 |
| R-seeding | 50 | 113035.3490 | 104281.8421 | +11.091 | +9.565 … +12.616 | 1/49 | <0.001 | <0.001 | 0.0002377 | 0.8745 | 0.000 | 0.000 |
| R-repair | 50 | 127355.6808 | 100841.0298 | +15.788 | +10.288 … +21.288 | 5/45 | <0.001 | <0.001 | 0.001600 | 0.6630 | 0.000 | 0.000 |

Gap to reduced by instance (%):

| configuration | C100 | T1 | T4 | T7 | T10 |
|---|---|---|---|---|---|
| R+LS3 | -0.112 | +0.081 | +0.187 | +0.392 | -1.463 |
| R+stagnation | -0.067 | -0.042 | -0.111 | -0.001 | +0.000 |
| reduced | +0.000 | +0.000 | +0.000 | +0.000 | +0.000 |
| R+mutation | -0.087 | +0.023 | +0.022 | +0.177 | +0.231 |
| R-cheaperBias | -0.123 | +0.022 | +0.144 | +0.272 | +0.108 |
| R+SAlight | -0.111 | -0.019 | +0.079 | +0.334 | +1.710 |
| R-ageing | +0.075 | +0.024 | +0.205 | +0.401 | +1.322 |
| R+LS30 | -0.064 | -0.063 | -0.132 | +0.205 | +3.755 |
| R+SA | -0.077 | -0.020 | +0.102 | +0.508 | +4.220 |
| R+SAstrong | -0.248 | +0.333 | +0.947 | +1.679 | +9.072 |
| R-recombination | -0.355 | +1.899 | +3.007 | +3.646 | +6.414 |
| R-tournament | +0.170 | +0.360 | +1.198 | +2.454 | +12.896 |
| full | +0.030 | +0.510 | +1.593 | +3.086 | +14.653 |
| R-LS | +4.509 | +1.672 | +3.726 | +5.230 | +25.572 |
| R-seeding | +1.193 | +11.569 | +13.323 | +16.017 | +13.351 |
| R-repair | -0.050 | +4.110 | +9.534 | +12.334 | +53.011 |

Gap: (cost − reduced cost) / reduced cost of the run with the same instance and repeat; negative is better. p: exact two-sided Wilcoxon signed-rank test of the gaps; Holm p: adjusted over the configurations.
<!-- results:end -->

## Conclusions

_Written 2026-10-28 after both parts finished (1,200 of 1,200 runs); numbers from `analysis.md` (analyze.py). Gap = (variant - R) / R, paired; positive = the variant is worse._

**The reduced GA is confirmed on new instances.** On instance seed 43 and new seeds, the full GA is worse than R by +3.98 % at 1M evaluations (6/44 pairs better/worse) and by +3.33 % at 300 s (6/44). That matches round 2 (-2.97 % and -3.45 % for R against the full GA), so the reduction is not tied to the round-1/2 instances. As before, the gap grows with instance size: from about 0 on C100 to +14.7 % (1M) and +12.0 % (300 s) on T10.

**Final classification (the decision rule of this round):**

| component | round 3, 1M | round 3, 300 s | FINAL | round 1 |
|---|---|---|---|---|
| ARX best-insertion repair | +15.79 % [10.29, 21.29], NEEDED | not tested | **WORTH KEEPING** | confirmed |
| greedy-based seeding | +11.09 % [9.57, 12.62], NEEDED | not tested | **WORTH KEEPING** | confirmed |
| local search of the offspring | +8.14 % [5.61, 10.68], NEEDED | +8.09 % [5.74, 10.43], NEEDED | **WORTH KEEPING** | confirmed |
| tournament-only selection (vs the roulette mix) | +3.42 % [2.03, 4.80], NEEDED | not tested | **WORTH KEEPING** | confirmed |
| recombination (ARX vs clone) | +2.92 % [2.28, 3.57], NEEDED | not tested | **WORTH KEEPING** | confirmed |
| age protection | +0.41 % [0.24, 0.58], NEEDED | not tested | **WORTH KEEPING** | new (round 1 inconclusive) |
| ARX cheaper-route bias | +0.08 % [-0.04, 0.21], UNDECIDED | not tested | UNDECIDED | round 1: ESSENTIAL (+1.47 %) |
| offspring mutation | +0.07 % [-0.05, 0.20], DOES NOT HELP | not tested | **NOT WORTH KEEPING** | confirmed |
| stagnation response | -0.04 % [-0.08, -0.01], DOES NOT HELP | not tested | **NOT WORTH KEEPING** | confirmed |
| SA on the best individual | worse with every schedule: R+SA +0.95 %, R+SAlight +0.40 %, R+SAstrong +2.36 % | better with every schedule: R+SA -1.16 % (49/1), R+SAlight -0.74 %, R+SAstrong -1.32 % (50/0) | **BUDGET-DEPENDENT** | round 1: REMOVABLE at 1M |

**Against the hypothesis.**
- *As expected:* seeding, repair, recombination and tournament selection are essential around R and on the new instances; mutation and the stagnation response do not help; at 1M evaluations no SA schedule helps; at 300 s SA's cheap evaluations make it pay.
- *Stronger than expected:* at 300 s SA does not just become competitive, it is better with every schedule. The strongest schedule (5 neighbors per temperature) is best: -1.32 %, 50/0.
- *Wrong twice.* Age protection is NEEDED (+0.41 %, with the most on T10: +1.32 %), where the hypothesis said inconclusive. The cheaper-route bias is UNDECIDED around R, at most about +0.2 %, where the hypothesis said it stays essential on large instances (T10: +0.11 %). The likely reason for the bias, an interpretation that was not tested: with 95 % duplicates, the two parents usually share most routes, so which parent's route ARX takes rarely matters. In the diverse full GA it did matter (+1.47 % in round 1). Age protection, inversely, matters more in a collapsed population: it keeps a fresh offspring from being replaced at once by the next, near-identical one.
- *Stagnation response:* adding it back is significantly better (Holm p = 0.026, 27/12), but by only 0.04 %, below the 0.1 % that the rule counts as a gain. It fires rarely around R, which keeps improving (median stagnation 41 generations, against the trigger at 100).

**Local search depth is budget-dependent as well.** At 1M, LS3 and LS30 both rate SAME AS LS10, but LS30 is +3.75 % on T10. At 300 s, LS30 is BETTER (-1.68 %, 46/4) and LS3 is WORSE (+2.13 %, 7/43).

**The common cause: an evaluation is not a fixed cost in this GA.** R makes only about 4,700 evaluations per second, because a generation pays for ARX with best-insertion repair and the replacement whatever its number of evaluations. SA and deep local search spend evaluations on cheap single-move neighbors: R+SA runs at 10,300 eval/s, R+LS30 at 11,600, R+SAstrong at 28,300. At equal evaluations these neighbor evaluations crowd out generations and lose; at equal time they come almost free and win. The evaluation budget therefore penalizes cheap-neighbor components, and for practical use, where the budget is wall time, the 300 s verdicts are the relevant ones. The speed ratios depend on the machine and the JIT, but the direction should not.

**Recommendation.**
1. Fixed: keep greedy seeding, ARX with best-insertion repair, recombination, local search of every offspring, tournament-only selection and age protection. Drop offspring mutation and the stagnation response. The cheaper-route bias costs nothing and was essential in the diverse GA, so keep it at 0.75 (undecided around R).
2. Time-limited runs (the lab, real use): SA on and local search deeper (LS30). Each helps alone at 300 s, by -1.16 % to -1.32 % (SA) and -1.68 % (LS30). **The combination R+SA+LS30 was not tested.** Both buy the same thing, cheap evaluations, so their gains may not add up. A short confirmatory round would settle this before changing the default: R, R+SA, R+LS30, R+SA+LS30 and R+SAstrong+LS30 at 300 s and 600 s on new seeds.
3. Evaluation-limited comparisons (the paper-style protocol): keep R as it is (SA off, LS10).
4. The code default (`PECTOHybridGA.builder`, the lab's "Hybrid GA") is R since 2026-10-27, as provisionally decided after round 2; the user makes the final call now that round 3 is in.
