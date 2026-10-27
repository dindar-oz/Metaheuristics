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
No runs finished yet.
<!-- results:end -->

## Conclusions

_Not written yet: read the results against the analysis plan and the decision rule, then write here what was decided and why._
