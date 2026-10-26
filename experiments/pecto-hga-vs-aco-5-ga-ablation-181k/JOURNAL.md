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
No runs finished yet.
<!-- results:end -->

## Conclusions

_Not written yet: read the results against the analysis plan and the decision rule, then write here what was decided and why._
