# Hybrid GA components, round 3, part 2: 300 s

`pecto-hga-components-r3-2-300s` · submitted 2026-10-27 08:39

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
  - `R-LS`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 0, local search steps = 0
  - `R+SA`: roulette = 0, mutation = none, stagnation period = 1000000
  - `R+SAlight`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 1, SA t0 = 5, SA t min = 0.5
  - `R+SAstrong`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 5
  - `R+LS3`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 0, local search steps = 3
  - `R+LS30`: roulette = 0, mutation = none, stagnation period = 1000000, SA inner loop = 0, local search steps = 30
- **Seeds:** repeats 0–9, seed = Seeds.derive(20261028, seedKey, repeat), the same in every configuration
- **Budget:** 300,000 ms per run
- **Runs:** 400
- **Trace:** every 1000 iterations (trace.jsonl)

## Analysis plan

Two experiments: pecto-hga-components-r3-1-1m (16 configurations, 1,000,000 evaluations) and pecto-hga-components-r3-2-300s (the 8 configurations whose evaluations cost differently: reduced, full, R-LS, the three SA variants, R+LS3, R+LS30; 300 s wall time). Instances: C100 (custom 10 ambulances / 100 patients / 5 hospitals x 30 beds) and Table I 1, 4, 7, 10, all generated with instance seed 43 (new instances, same sizes as rounds 1-2); run seeds base 20261028, repeats 0-9. Baseline 'reduced' (R): mixed greedy seeding, ARX (cheaper-route 0.75, best insertion), local search 10 steps, tournament-only selection, no mutation, no stagnation response, no SA, age protection 3. PRIMARY, per budget and variant over the 50 pairs (same instance and repeat): paired gap (variant - R) / R in %, mean with 95 % t-interval, better/worse, exact Wilcoxon signed-rank, Holm over the variants of that budget (15 at 1M, 7 at 300 s); per instance descriptive. The final classification combines round 1 (part 6, computed from its data) and round 3, per component. SECONDARY: full vs reduced (replication of round 2 on new instances); setting sensitivity of SA and local search; activity at the end (duplicates, diversity, evaluations per second, generations). Analysis script: analyze.py in the folder of pecto-hga-components-r3-1-1m, written before any result.

## Decision rule

Per budget, for a KEPT component (variant R-x): it is NEEDED if R-x is significantly worse (Holm p < 0.05, mean gap >= +0.1 %); NOT NEEDED if the upper end of the 95 % interval of the mean gap is below +0.1 %; else UNDECIDED. For a DROPPED component (R+x): it HELPS if R+x is significantly better (Holm p < 0.05, mean gap <= -0.1 %); it DOES NOT HELP if the lower end of the 95 % interval is above -0.1 % (adding it gains less than 0.1 %); else UNDECIDED. SA and local search are judged on their best tested setting: SA helps if any of R+SA, R+SAlight, R+SAstrong helps; local search is needed if R-LS is worse than R (and its setting sensitivity is reported from R+LS3 / R+LS30). FINAL, per component, decided by round 3 (new data, and the reduced GA is the design being kept): WORTH KEEPING if NEEDED (or, for a dropped component, HELPS) at every budget it was tested at; NOT WORTH KEEPING if NOT NEEDED / DOES NOT HELP at every tested budget; BUDGET-DEPENDENT if it is worth keeping at one budget and not at the other; otherwise UNDECIDED. Round 1 is reported beside it: CONFIRMED if round 1 points the same way, CONTRADICTS ROUND 1 otherwise (an interaction with the removed mechanisms or an instance effect), and NEW if round 1 was inconclusive. The 1M verdict covers every component; the 300 s verdict only those tested at 300 s. Conclusions go in the journal of pecto-hga-components-r3-1-1m.

## Notes

Part of round 3 (two parts). The design notes and the conclusions are in [the round-3 hub journal](../pecto-hga-components-r3-1-1m/JOURNAL.md).

**2026-10-27: paused during a lab session.** While the user ran the Metaheuristic Lab, this time-limited part was paused before any of its runs had started, and resumed afterwards. None of its runs shared the CPU with the lab. During the session only evaluation-limited runs were in flight, whose results do not depend on CPU load.

**2026-10-27 09:41: 7 runs stopped after about a minute.** The worker started this part's first runs in the seconds between pausing round 2's 600 s part and this one; they were stopped at once (`pause --now`) and run again from the start later, when PECTOACO development and benchmarking were finished. No finished run overlapped extra load.

## Results

<!-- results:start (written by the worker; edit outside this block) -->
_Updated 2026-10-28 00:27; also in summary.md._

400 of 400 runs finished.

| configuration | n | mean cost | median | gap to reduced % | 95% interval | better/worse | p | Holm p | diversity | duplicates | immigrations | mutationRate |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| R+LS30 | 50 | 97049.0669 | 90288.1491 | -1.684 | -2.179 … -1.190 | 46/4 | <0.001 | <0.001 | 0.0002567 | 0.8535 | 0.000 | 0.000 |
| R+SAstrong | 50 | 98012.4703 | 90344.8988 | -1.316 | -1.573 … -1.059 | 50/0 | <0.001 | <0.001 | 0.0004533 | 0.8490 | 0.000 | 0.000 |
| R+SA | 50 | 98003.6493 | 90740.8158 | -1.162 | -1.501 … -0.822 | 49/1 | <0.001 | <0.001 | 0.0003045 | 0.8330 | 0.000 | 0.000 |
| R+SAlight | 50 | 98605.6315 | 91150.9022 | -0.744 | -1.020 … -0.469 | 46/4 | <0.001 | <0.001 | 0.0001906 | 0.8865 | 0.000 | 0.000 |
| reduced | 50 | 99832.6450 | 91493.4699 | +0.000 | +0.000 … +0.000 | 0/0 | 1.000 | 1.000 | 0.0001469 | 0.9145 | 0.000 | 0.000 |
| R+LS3 | 50 | 103441.2826 | 92980.7665 | +2.133 | +1.482 … +2.784 | 7/43 | <0.001 | <0.001 | 0.0001219 | 0.9185 | 0.000 | 0.000 |
| full | 50 | 105966.1894 | 93031.2432 | +3.333 | +2.055 … +4.611 | 6/44 | <0.001 | <0.001 | 0.04165 | 0.02400 | 614.8 | 0.7500 |
| R-LS | 50 | 112666.2265 | 95446.4115 | +8.088 | +5.743 … +10.434 | 0/50 | <0.001 | <0.001 | 0.000 | 0.9750 | 0.000 | 0.000 |

Gap to reduced by instance (%):

| configuration | C100 | T1 | T4 | T7 | T10 |
|---|---|---|---|---|---|
| R+LS30 | -0.064 | -0.557 | -1.332 | -1.659 | -4.809 |
| R+SAstrong | -0.464 | -0.564 | -1.357 | -1.659 | -2.537 |
| R+SA | -0.326 | -0.421 | -0.890 | -1.004 | -3.168 |
| R+SAlight | -0.208 | -0.236 | -0.466 | -0.549 | -2.262 |
| reduced | +0.000 | +0.000 | +0.000 | +0.000 | +0.000 |
| R+LS3 | -0.112 | +0.691 | +1.547 | +2.279 | +6.260 |
| full | -0.064 | +0.542 | +1.564 | +2.673 | +11.948 |
| R-LS | +4.509 | +2.073 | +4.222 | +5.422 | +24.216 |

Gap: (cost − reduced cost) / reduced cost of the run with the same instance and repeat; negative is better. p: exact two-sided Wilcoxon signed-rank test of the gaps; Holm p: adjusted over the configurations.
<!-- results:end -->

## Conclusions

_Not written yet: read the results against the analysis plan and the decision rule, then write here what was decided and why._
