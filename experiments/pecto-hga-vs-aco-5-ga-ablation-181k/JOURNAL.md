# Hybrid GA vs PECTOACO, part 5: ablation of every GA mechanism, 181,501 evaluations

`pecto-hga-vs-aco-5-ga-ablation-181k` · submitted 2026-10-26 20:05

## Question

Which mechanisms of the hybrid GA are effective, and which can be removed without loss? Every mechanism is switched off (or replaced by a generic alternative) one at a time from the full GA: domain-aware seeding, the roulette/tournament selection mix, the two domain-aware parts of ARX (cheaper-route preference, best-insertion repair), ARX itself, the domain-aware mutation, the mutation-rate growth, immigration, perturbation and the whole stagnation response, the age protection of new individuals, local search of the offspring and SA on the best. Requested by the user on 2026-10-26, after parts 1-4 were submitted and before any of their results were read.

## Hypothesis

Probes and the first part-1 runs: the stagnation response never triggered within 181,501 evaluations (0 immigrations on T10 and T1) and triggered 67 times in 2M evaluations on T10 while the run kept stagnating (1545 generations without improvement at the end, mutation rate at its 0.75 cap); at 181,501 evaluations 16-86 % of the population were duplicates. Expectations: (a) ARX's best-insertion repair and the domain-aware seeding matter most (they inject greedy structure); (b) SA and local search matter, plain is the worst refinement variant; (c) immigration, perturbation, rate growth and age protection have no detectable effect (removable); (d) the domain-aware mutation does not beat MoveTrip at rate 0.05; (e) no crossover (clone) is clearly worse than ARX, i.e. recombination itself contributes; (f) at 181,501 evaluations the stagnation variants equal the default.

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
  - `seedRandom`: seeding = random
  - `selRoulette`: roulette = 1
  - `selTournament`: roulette = 0
  - `xUnbiased`: cheaper route = 0.5
  - `xRandomRepair`: ARX repair = random
  - `xUniform`: crossover = uniform route
  - `xNone`: crossover = none
  - `mMoveTrip`: mutation = move trip
  - `mNone`: mutation = none
  - `mFixedRate`: mutation rate growth = 1
  - `noImmigration`: immigration = off
  - `noPerturbation`: perturbation = off
  - `noStagnation`: stagnation period = 1000000
  - `noAgeing`: min victim age = 0
  - `noLS`: local search steps = 0
  - `noSA`: SA inner loop = 0
  - `plain`: hybrid = off
- **Seeds:** repeats 0–9, seed = Seeds.derive(20261026, seedKey, repeat), the same in every configuration
- **Budget:** 181,501 evaluations per run
- **Runs:** 900
- **Trace:** every 1000 iterations (trace.jsonl)

## Analysis plan

Parts 5 and 6 of the comparison pecto-hga-vs-aco (hub journal: pecto-hga-vs-aco-1-ga-evals): the same 18 configurations at 181,501 evaluations (part 5, pecto-hga-vs-aco-5-ga-ablation-181k, final results only) and at 1,000,000 evaluations (part 6, pecto-hga-vs-aco-6-ga-ablation-1m, about 5,000 generations, so the stagnation response acts many times). Same 5 instances and seeds (base 20261026, repeats 0-9) as parts 1-4. Each configuration is the default with ONE mechanism removed or replaced (one-factor-at-a-time; plain = noLS + noSA is the only combination). Evaluation budgets make runs deterministic, so every configuration is paired with 'default' by instance and repeat. REPLICATION CHECK: part 5's default, noSA, noLS and plain repeat part 1's runs exactly (same code path, seeds and budget), so their final costs must be identical. PRIMARY (part 6), per configuration over the 50 pairs: final best cost as the paired gap to 'default' in % ((variant - default) / default), mean with 95 % t-interval, better/worse counts, exact two-sided Wilcoxon signed-rank, Holm over the 17 variants; per instance (10 pairs) descriptive, to see whether an effect depends on size or share of beds used. SECONDARY: the same table for part 5 (a mechanism can help early and not late, or the reverse); anytime median curves from part 6's traces (every 250 generations); activity at the end (immigrations, mutation rate, duplicates, diversity, stagnation); evaluations per second (a removal can make the GA faster per evaluation, which matters for the equal-time comparison). Group reading: seeding (seedRandom); selection (selRoulette, selTournament); crossover (xUnbiased, xRandomRepair, xUniform, xNone); mutation (mMoveTrip, mNone, mFixedRate); stagnation (noImmigration, noPerturbation, noStagnation); replacement (noAgeing); refinement (noLS, noSA, plain).

## Decision rule

At 1M evaluations (part 6), for every variant: ESSENTIAL (keep the mechanism) if removing it is significantly worse (Holm p < 0.05) and the mean gap is at least +0.1 %; HARMFUL (remove it) if removing it is significantly better by the same criteria; REMOVABLE if neither and the upper end of the 95 % interval of the mean gap is below +0.1 % (removal costs less than 0.1 %); otherwise INCONCLUSIVE (keep for now). For replacements (selRoulette, selTournament, xUniform, mMoveTrip) the same labels say whether the default choice beats the alternative. The verdict at 181,501 evaluations (part 5) is reported separately and does not change the 1M verdict. Because one-at-a-time removals can hide interactions (two removable mechanisms may each compensate for the other), a follow-up experiment (round 2, specified after this one) removes all REMOVABLE and HARMFUL mechanisms together and tests the reduced GA against the default on new seeds (base 20261027) at 181,501 and 1M evaluations and at 600 s: the reduction is accepted if the reduced GA is not worse by 0.1 % or more (upper end of the 95 % interval below +0.1 %). Conclusions go in the hub journal.

## Notes

Part of a six-part comparison; parts 5 and 6 are the same ablation at two budgets. The design notes (every configuration's exact lab setting, the code change and its replay check, the worker restart) and the joint conclusions are in [the hub journal](../pecto-hga-vs-aco-1-ga-evals/JOURNAL.md). Record here only what concerns this part alone, such as interruptions.

## Results

<!-- results:start (written by the worker; edit outside this block) -->
_Updated 2026-10-27 00:41; also in summary.md._

900 of 900 runs finished.

| configuration | n | mean cost | median | gap to default % | 95% interval | better/worse | p | Holm p | diversity | duplicates | immigrations | mutationRate |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| selTournament | 50 | 104307.2578 | 95072.1195 | -0.982 | -1.305 … -0.659 | 45/5 | <0.001 | <0.001 | 0.001498 | 0.6670 | 1.020 | 0.05609 |
| noSA | 50 | 105315.0501 | 95096.2133 | -0.380 | -0.559 … -0.202 | 36/14 | <0.001 | <0.001 | 0.01857 | 0.4065 | 9.280 | 0.1389 |
| plain | 50 | 105369.0851 | 94921.8392 | -0.158 | -0.413 … +0.097 | 37/13 | 0.133 | 1.000 | 0.03250 | 0.04750 | 133.2 | 0.7500 |
| noAgeing | 50 | 105660.4719 | 95475.7057 | -0.102 | -0.259 … +0.055 | 29/21 | 0.230 | 1.000 | 0.02058 | 0.2275 | 2.840 | 0.06712 |
| noPerturbation | 50 | 105797.6908 | 95472.6829 | -0.078 | -0.137 … -0.019 | 31/12 | 0.014 | 0.130 | 0.01464 | 0.3330 | 2.700 | 0.06590 |
| mFixedRate | 50 | 105847.2965 | 95402.2591 | -0.037 | -0.087 … +0.012 | 26/16 | 0.243 | 1.000 | 0.01705 | 0.3465 | 3.040 | 0.05000 |
| mNone | 50 | 105870.2323 | 95590.5866 | -0.013 | -0.204 … +0.178 | 26/24 | 0.796 | 1.000 | 0.01200 | 0.4260 | 2.500 | 0.000 |
| noImmigration | 50 | 105859.4136 | 95406.1021 | -0.013 | -0.079 … +0.053 | 20/22 | 0.629 | 1.000 | 0.01363 | 0.2945 | 2.720 | 0.06591 |
| default | 50 | 105916.1911 | 95483.4952 | +0.000 | +0.000 … +0.000 | 0/0 | 1.000 | 1.000 | 0.02588 | 0.3175 | 2.780 | 0.06628 |
| noStagnation | 50 | 105901.4938 | 95575.2602 | +0.004 | -0.051 … +0.059 | 18/24 | 0.435 | 1.000 | 0.01285 | 0.3875 | 0.000 | 0.05000 |
| noLS | 50 | 105889.4031 | 95593.4559 | +0.023 | -0.119 … +0.166 | 17/33 | 0.372 | 1.000 | 0.01422 | 0.4180 | 7.020 | 0.1023 |
| mMoveTrip | 50 | 106024.3080 | 95380.3277 | +0.041 | -0.118 … +0.200 | 24/26 | 0.572 | 1.000 | 0.02520 | 0.2205 | 2.780 | 0.06623 |
| selRoulette | 50 | 106846.4638 | 95687.3102 | +0.634 | +0.395 … +0.872 | 7/43 | <0.001 | <0.001 | 0.02919 | 0.1785 | 4.760 | 0.07999 |
| xUnbiased | 50 | 109098.6537 | 96945.1061 | +1.903 | +1.353 … +2.454 | 8/42 | <0.001 | <0.001 | 0.02106 | 0.2300 | 2.200 | 0.06243 |
| xNone | 50 | 116595.4037 | 102797.7097 | +7.204 | +5.821 … +8.588 | 7/43 | <0.001 | <0.001 | 0.01044 | 0.7435 | 1.620 | 0.05937 |
| xRandomRepair | 50 | 120585.2113 | 104270.9639 | +10.055 | +8.283 … +11.826 | 0/50 | <0.001 | <0.001 | 0.2467 | 0.000 | 9.860 | 0.1281 |
| xUniform | 50 | 120636.7294 | 104280.7204 | +10.060 | +8.271 … +11.849 | 0/50 | <0.001 | <0.001 | 0.4337 | 0.000 | 10.18 | 0.1320 |
| seedRandom | 50 | 136598.8705 | 125836.9744 | +23.577 | +20.139 … +27.015 | 1/49 | <0.001 | <0.001 | 0.01766 | 0.1975 | 0.6800 | 0.05392 |

Gap to default by instance (%):

| configuration | C100 | T1 | T4 | T7 | T10 |
|---|---|---|---|---|---|
| selTournament | -0.513 | -0.039 | -0.304 | -1.348 | -2.706 |
| noSA | -0.046 | -0.217 | -0.343 | -0.326 | -0.970 |
| plain | +1.350 | -0.413 | -0.563 | -0.607 | -0.557 |
| noAgeing | +0.168 | -0.056 | +0.110 | -0.284 | -0.445 |
| noPerturbation | -0.043 | -0.016 | -0.018 | -0.157 | -0.156 |
| mFixedRate | +0.001 | +0.039 | -0.051 | -0.078 | -0.099 |
| mNone | +0.108 | -0.126 | +0.109 | -0.145 | -0.013 |
| noImmigration | -0.004 | +0.091 | +0.054 | -0.067 | -0.140 |
| default | +0.000 | +0.000 | +0.000 | +0.000 | +0.000 |
| noStagnation | -0.073 | +0.134 | +0.086 | -0.046 | -0.079 |
| noLS | +0.200 | -0.061 | +0.111 | -0.117 | -0.016 |
| mMoveTrip | -0.013 | -0.037 | -0.087 | +0.086 | +0.257 |
| selRoulette | +0.490 | +0.086 | +0.330 | +0.784 | +1.478 |
| xUnbiased | -0.220 | +0.582 | +1.613 | +2.811 | +4.731 |
| xNone | -0.216 | +4.177 | +7.676 | +12.269 | +12.115 |
| xRandomRepair | +2.247 | +4.722 | +9.260 | +16.143 | +17.901 |
| xUniform | +2.107 | +4.748 | +9.265 | +16.196 | +17.985 |
| seedRandom | +0.580 | +25.677 | +32.539 | +29.575 | +29.517 |

Gap: (cost − default cost) / default cost of the run with the same instance and repeat; negative is better. p: exact two-sided Wilcoxon signed-rank test of the gaps; Holm p: adjusted over the configurations.
<!-- results:end -->

## Conclusions

_Written 2026-10-27._ This part is one of six; the conclusions of the whole comparison (Hybrid GA vs PECTOACO, and which GA mechanisms work) are in [the hub journal](../pecto-hga-vs-aco-1-ga-evals/JOURNAL.md#conclusions), drawn from `analysis.md` there. The results table above compares configurations within this part only.
