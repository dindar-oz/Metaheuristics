# Hybrid GA vs PECTOACO, part 6: ablation of every GA mechanism, 1M evaluations

`pecto-hga-vs-aco-6-ga-ablation-1m` · submitted 2026-10-26 20:05

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
- **Budget:** 1,000,000 evaluations per run
- **Runs:** 900
- **Trace:** every 250 iterations (trace.jsonl)

## Analysis plan

Parts 5 and 6 of the comparison pecto-hga-vs-aco (hub journal: pecto-hga-vs-aco-1-ga-evals): the same 18 configurations at 181,501 evaluations (part 5, pecto-hga-vs-aco-5-ga-ablation-181k, final results only) and at 1,000,000 evaluations (part 6, pecto-hga-vs-aco-6-ga-ablation-1m, about 5,000 generations, so the stagnation response acts many times). Same 5 instances and seeds (base 20261026, repeats 0-9) as parts 1-4. Each configuration is the default with ONE mechanism removed or replaced (one-factor-at-a-time; plain = noLS + noSA is the only combination). Evaluation budgets make runs deterministic, so every configuration is paired with 'default' by instance and repeat. REPLICATION CHECK: part 5's default, noSA, noLS and plain repeat part 1's runs exactly (same code path, seeds and budget), so their final costs must be identical. PRIMARY (part 6), per configuration over the 50 pairs: final best cost as the paired gap to 'default' in % ((variant - default) / default), mean with 95 % t-interval, better/worse counts, exact two-sided Wilcoxon signed-rank, Holm over the 17 variants; per instance (10 pairs) descriptive, to see whether an effect depends on size or share of beds used. SECONDARY: the same table for part 5 (a mechanism can help early and not late, or the reverse); anytime median curves from part 6's traces (every 250 generations); activity at the end (immigrations, mutation rate, duplicates, diversity, stagnation); evaluations per second (a removal can make the GA faster per evaluation, which matters for the equal-time comparison). Group reading: seeding (seedRandom); selection (selRoulette, selTournament); crossover (xUnbiased, xRandomRepair, xUniform, xNone); mutation (mMoveTrip, mNone, mFixedRate); stagnation (noImmigration, noPerturbation, noStagnation); replacement (noAgeing); refinement (noLS, noSA, plain).

## Decision rule

At 1M evaluations (part 6), for every variant: ESSENTIAL (keep the mechanism) if removing it is significantly worse (Holm p < 0.05) and the mean gap is at least +0.1 %; HARMFUL (remove it) if removing it is significantly better by the same criteria; REMOVABLE if neither and the upper end of the 95 % interval of the mean gap is below +0.1 % (removal costs less than 0.1 %); otherwise INCONCLUSIVE (keep for now). For replacements (selRoulette, selTournament, xUniform, mMoveTrip) the same labels say whether the default choice beats the alternative. The verdict at 181,501 evaluations (part 5) is reported separately and does not change the 1M verdict. Because one-at-a-time removals can hide interactions (two removable mechanisms may each compensate for the other), a follow-up experiment (round 2, specified after this one) removes all REMOVABLE and HARMFUL mechanisms together and tests the reduced GA against the default on new seeds (base 20261027) at 181,501 and 1M evaluations and at 600 s: the reduction is accepted if the reduced GA is not worse by 0.1 % or more (upper end of the 95 % interval below +0.1 %). Conclusions go in the hub journal.

## Notes

Part of a six-part comparison; parts 5 and 6 are the same ablation at two budgets. The design notes (every configuration's exact lab setting, the code change and its replay check, the worker restart) and the joint conclusions are in [the hub journal](../pecto-hga-vs-aco-1-ga-evals/JOURNAL.md). Record here only what concerns this part alone, such as interruptions.

## Results

<!-- results:start (written by the worker; edit outside this block) -->
_Updated 2026-10-27 07:50; also in summary.md._

900 of 900 runs finished.

| configuration | n | mean cost | median | gap to default % | 95% interval | better/worse | p | Holm p | diversity | duplicates | immigrations | mutationRate |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| selTournament | 50 | 99601.9889 | 92994.3022 | -2.451 | -3.122 … -1.780 | 50/0 | <0.001 | <0.001 | 0.01394 | 0.5760 | 11.74 | 0.2200 |
| mNone | 50 | 103403.8547 | 94336.7791 | -0.167 | -0.309 … -0.026 | 35/15 | 0.0039 | 0.031 | 0.02621 | 0.4315 | 29.06 | 0.000 |
| mFixedRate | 50 | 103363.4390 | 94275.1175 | -0.166 | -0.287 … -0.045 | 35/15 | 0.0052 | 0.037 | 0.02959 | 0.3725 | 29.18 | 0.05000 |
| noStagnation | 50 | 103316.5202 | 94204.3667 | -0.157 | -0.254 … -0.059 | 32/18 | 0.0061 | 0.037 | 0.006716 | 0.4885 | 0.000 | 0.05000 |
| mMoveTrip | 50 | 103438.3908 | 94253.0795 | -0.111 | -0.240 … +0.018 | 29/21 | 0.084 | 0.336 | 0.05439 | 0.01650 | 29.04 | 0.5441 |
| noPerturbation | 50 | 103401.1901 | 94349.4547 | -0.109 | -0.204 … -0.014 | 32/18 | 0.027 | 0.134 | 0.04486 | 0.03450 | 30.56 | 0.5828 |
| noImmigration | 50 | 103445.7407 | 94320.3926 | -0.056 | -0.169 … +0.057 | 25/25 | 0.566 | 1.000 | 0.02833 | 0.08050 | 27.40 | 0.5168 |
| noSA | 50 | 103490.9148 | 94434.8814 | -0.030 | -0.141 … +0.081 | 27/23 | 0.619 | 1.000 | 0.03769 | 0.03600 | 90.08 | 0.7500 |
| default | 50 | 103574.2311 | 94371.0122 | +0.000 | +0.000 … +0.000 | 0/0 | 1.000 | 1.000 | 0.06665 | 0.01900 | 31.30 | 0.6043 |
| noAgeing | 50 | 103684.3232 | 94437.5597 | +0.060 | -0.110 … +0.229 | 26/24 | 0.796 | 1.000 | 0.04126 | 0.02350 | 31.48 | 0.6186 |
| noLS | 50 | 103938.5498 | 94572.8849 | +0.268 | +0.147 … +0.390 | 11/39 | <0.001 | <0.001 | 0.04019 | 0.02800 | 62.60 | 0.7500 |
| plain | 50 | 104424.4887 | 94499.3918 | +0.614 | +0.394 … +0.834 | 16/34 | <0.001 | <0.001 | 0.03662 | 0.05400 | 733.0 | 0.7500 |
| selRoulette | 50 | 105156.1560 | 95189.0317 | +1.050 | +0.780 … +1.321 | 3/47 | <0.001 | <0.001 | 0.05960 | 0.005000 | 40.60 | 0.7469 |
| xUnbiased | 50 | 106151.0717 | 95169.2446 | +1.469 | +0.934 … +2.004 | 11/39 | <0.001 | <0.001 | 0.05830 | 0.06250 | 25.44 | 0.4905 |
| xNone | 50 | 109886.8707 | 99759.9185 | +4.710 | +3.924 … +5.495 | 8/42 | <0.001 | <0.001 | 0.05191 | 0.4560 | 19.94 | 0.3662 |
| seedRandom | 50 | 116711.8051 | 106470.7987 | +10.183 | +8.702 … +11.663 | 0/50 | <0.001 | <0.001 | 0.02737 | 0.2905 | 9.500 | 0.1964 |
| xRandomRepair | 50 | 120364.2373 | 104207.8773 | +11.563 | +9.382 … +13.743 | 0/50 | <0.001 | <0.001 | 0.3286 | 0.000 | 55.96 | 0.7500 |
| xUniform | 50 | 120365.8620 | 104230.5613 | +11.570 | +9.395 … +13.745 | 0/50 | <0.001 | <0.001 | 0.4495 | 0.000 | 56.80 | 0.7500 |

Gap to default by instance (%):

| configuration | C100 | T1 | T4 | T7 | T10 |
|---|---|---|---|---|---|
| selTournament | -0.340 | -0.441 | -1.475 | -3.591 | -6.409 |
| mNone | -0.041 | -0.282 | -0.193 | -0.301 | -0.020 |
| mFixedRate | -0.049 | -0.153 | -0.153 | -0.274 | -0.201 |
| noStagnation | -0.006 | +0.045 | -0.206 | -0.220 | -0.396 |
| mMoveTrip | +0.016 | -0.147 | -0.228 | -0.062 | -0.134 |
| noPerturbation | +0.029 | -0.053 | -0.104 | -0.192 | -0.226 |
| noImmigration | -0.055 | +0.135 | -0.040 | +0.061 | -0.381 |
| noSA | +0.038 | -0.077 | +0.085 | +0.070 | -0.267 |
| default | +0.000 | +0.000 | +0.000 | +0.000 | +0.000 |
| noAgeing | +0.031 | -0.033 | +0.002 | +0.049 | +0.250 |
| noLS | +0.222 | +0.043 | +0.169 | +0.372 | +0.536 |
| plain | +0.887 | -0.281 | +0.095 | +0.903 | +1.465 |
| selRoulette | +0.365 | +0.167 | +0.793 | +1.573 | +2.354 |
| xUnbiased | -0.235 | +0.354 | +0.758 | +1.914 | +4.553 |
| xNone | -0.124 | +4.080 | +5.726 | +7.729 | +6.137 |
| seedRandom | +0.367 | +11.152 | +13.155 | +12.346 | +13.893 |
| xRandomRepair | +2.020 | +5.076 | +10.365 | +18.722 | +21.630 |
| xUniform | +1.991 | +5.158 | +10.381 | +18.724 | +21.600 |

Gap: (cost − default cost) / default cost of the run with the same instance and repeat; negative is better. p: exact two-sided Wilcoxon signed-rank test of the gaps; Holm p: adjusted over the configurations.
<!-- results:end -->

## Conclusions

_Written 2026-10-27._ This part is one of six; the conclusions of the whole comparison (Hybrid GA vs PECTOACO, and which GA mechanisms work) are in [the hub journal](../pecto-hga-vs-aco-1-ga-evals/JOURNAL.md#conclusions), drawn from `analysis.md` there. The results table above compares configurations within this part only.
