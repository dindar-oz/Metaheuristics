# Hybrid GA vs PECTOACO, part 1: GA and ablations, equal evaluations

`pecto-hga-vs-aco-1-ga-evals` · submitted 2026-10-26 17:02

## Question

Does the research hybrid GA (metaheuristic.hybridga, PECTOHybridGA with its default parameters) find cheaper PECTO solutions than PECTOACO with its tuned defaults, under (a) equal evaluations and (b) equal wall time, and how does the answer depend on the instance (size, share of beds used)? Prompted by a lab observation (2026-10-26) that the GA reached lower cost and higher diversity than PECTOACO.

## Hypothesis

From probes on 2026-10-26 (single thread, instance seed 42, GA seed 1): on T10 the GA reached 212,847 after 20k evaluations, 208,655 after 181,501 (18 s) and 201,987 after 2M (190 s), against about 172,000 for PECTOACO's default after 181,501 evaluations (pecto-explorer-tuning-r1/r2). Greedy + OptimalHospitals alone costs 197,377 on T10, better than the GA after 2M evaluations: on T10 99.6 % of beds are used, and only PECTOACO assigns hospitals optimally (on its best ant every iteration). H1 (equal evaluations): PECTOACO is better on T7 and T10 by more than 10 %; the gap shrinks as fewer beds are used, and the GA may win on C100/T1. H2 (equal time, 600 s): the GA's ~40x higher evaluation rate narrows the gap, but PECTOACO still wins on T10. H3 (ablation): local search and SA account for most of the GA's quality (plain GA clearly worse). H4: the GA's final population diversity is lower than PECTOACO's (probe: 0.005 vs about 0.15), so the lab observation of higher GA diversity was probably on another instance or early in a run.

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
  - `noSA`: SA inner loop = 0
  - `noLS`: local search steps = 0
  - `plain`: hybrid = off
- **Seeds:** repeats 0–9, seed = Seeds.derive(20261026, seedKey, repeat), the same in every configuration
- **Budget:** 181,501 evaluations per run
- **Runs:** 200
- **Trace:** every 25 iterations (trace.jsonl)

## Analysis plan

Four experiments form one comparison: pecto-hga-vs-aco-1-ga-evals (GA default + 3 ablations, 181,501 evaluations), -2-aco-evals (PECTOACO default, 181,501 evaluations = 1500 ACO iterations, its tuning budget), -3-ga-time and -4-aco-time (default of each, 600 s wall time). All four use the same 5 instances (C100: lab custom 10 ambulances / 100 patients / 5 hospitals x 30 beds, 67 % of beds used; Table I 1, 4, 7, 10 with instance seed 42: 2430/3341/4252/5163 patients for 5184 beds = 47/64/82/99.6 %) and the same seeds (base 20261026, repeats 0-9). The seed base is new: PECTOACO was tuned with base 20261016 on T8-T10, so no run here reuses a tuning seed; T10 itself was a tuning instance, T1/T4/T7/C100 were not, and the GA was not tuned at all. Within one algorithm, runs with the same instance and repeat share their seed; across algorithms a shared seed means nothing, so the GA-vs-ACO comparison is unpaired. PRIMARY, per budget type and instance: final best cost of GA 'default' vs ACO 'default' (10 vs 10 runs): exact two-sided Mann-Whitney U, Holm over the 5 instances; size as the gap of medians (GA - ACO) / ACO in % and Vargha-Delaney A12 (probability that a GA run beats an ACO run). Computed from the runs.csv of the parts (the runner's own summary only compares configurations within one experiment). SECONDARY: (1) anytime curves: median best cost against evaluations (parts 1+2) and against elapsed time (parts 3+4) per instance, and where they cross; (2) ablation in part 1: noSA, noLS, plain vs default, paired by instance and repeat (exact Wilcoxon over 50 pairs, Holm over the 3), and plain GA vs ACO; (3) gap vs share of beds used (5 points, descriptive); (4) diversity and duplicates at the end, descriptive only: for the GA they measure its population, for PECTOACO the ants of the last iteration (both sampled on 16 members), so they are not the same quantity; (5) evaluations per second and generations/iterations in the time parts. Time runs: wall clock, 7 runs in parallel on 8 logical processors; runs near the end of the queue share the machine with fewer others, so check evaluations/s by start order before trusting small time-based differences.

## Decision rule

Decided separately for equal evaluations (parts 1+2) and equal time (parts 3+4), on final best cost only. Per instance, an algorithm wins if Holm-adjusted p < 0.05 and the median gap is at least 0.1 % (smaller significant differences count as a tie). Verdict 'GA better' if the GA wins on at least 3 of 5 instances and loses on none; 'PECTOACO better' symmetrically; otherwise 'instance-dependent', stated as which algorithm wins where, related to size and share of beds used. The significance test decides, the effect sizes only describe; diversity never enters the verdict. Ablation: a GA component matters if removing it worsens the final cost significantly (Holm p < 0.05 over the 3 ablations). The joint analysis and the conclusions are written in the journal of pecto-hga-vs-aco-1-ga-evals; the other parts' journals point there.

## Notes

_Written 2026-10-26 at submission, before any result. This journal is the hub of the four-part comparison: the joint analysis and the conclusions go here._

**The parts** (same instances, seeds base 20261026 repeats 0-9, instance seed 42, 7 runs in parallel):

| part | experiment | algorithm, configurations | budget | trace every |
|---|---|---|---|---|
| 1 | `pecto-hga-vs-aco-1-ga-evals` | Hybrid GA: default, noSA, noLS, plain | 181,501 evaluations | 25 generations |
| 2 | `pecto-hga-vs-aco-2-aco-evals` | ACO (MMAS): default | 181,501 evaluations | 25 iterations |
| 3 | `pecto-hga-vs-aco-3-ga-time` | Hybrid GA: default | 600 s wall time | 200 generations |
| 4 | `pecto-hga-vs-aco-4-aco-time` | ACO (MMAS): default | 600 s wall time | 25 iterations |
| 5 | `pecto-hga-vs-aco-5-ga-ablation-181k` | Hybrid GA: default + 17 one-mechanism ablations (added later, see below) | 181,501 evaluations | 1000 generations |
| 6 | `pecto-hga-vs-aco-6-ga-ablation-1m` | Hybrid GA: the same 18 configurations | 1,000,000 evaluations | 250 generations |

All four started on runtime `r-20261026-170301-656dbd4cf3` (worker pid 22480, 17:03). If the worker is restarted, record the new runtime and whether algorithm classes changed.

**Code under test.**
- *Hybrid GA:* `metaheuristic.hybridga`, written 2026-10-26. It is a port of `research/ga/pecto/PECTOGA` to the framework. It runs through the lab entry "Hybrid GA" (`Catalog.pectoHybridGa` → `PECTOHybridGA.builder`).
- *PECTOACO:* unchanged since pecto-explorer-tuning-r2; `PECTOACOGoldenTest` still passes.

**GA defaults as run (configuration `default`):**
- *Population:* 40. 20 come from `RandomPECTOSG`, 10 from `PartialGreedyPECTOSG` (keeps each greedy route with probability 0.9) and 10 from `RandomizedGreedyPECTOSG` (3 candidate ambulances).
- *Parents:* 5 per generation, by roulette on 1/cost with probability 0.75, else by tournament of 5.
- *Crossover:* ARX on every pair of parents with rate 0.75, taking the cheaper route with probability 0.75. That gives about 7.5 children per generation.
- *Mutation:* `CostliestRouteMove` on each child with rate 0.05. The rate is multiplied by 1.1 at each stagnation, up to 0.75.
- *Stagnation:* after 100 generations without a new population best: 4 migrants from the three generators in turn, then `SwapParticipants` on 36 of the 40 individuals. The offspring are not mutated in that generation.
- *Replacement:* victims at least 3 generations old, by roulette on cost. There is no elitism.
- *Hybrid step:* first-improvement local search with up to 10 `SwapParticipants` neighbors per child. Then SA on the population best, from T = 25 down to 0.1 with factor 0.95 and 1 neighbor per temperature, which is 108 evaluations per generation.
- *Throughput:* about 190 evaluations per generation, so 181,501 evaluations are about 950 generations.
- *Ablations:* `noSA` sets the SA inner loop to 0 (local search stays). `noLS` sets the local search steps to 0 (SA stays). `plain` turns hybrid off (neither).
- *Research values vs. guesses:* the research code gives the population mix, the selection, ARX, the mutation and the stagnation response with their rates. **My assumptions**, because the research classes for local search, SA and greedy were not in the folder: the local search budget (10), the SA schedule (25 → 0.1, ×0.95, 1 neighbor per temperature), `SwapParticipants` as the neighborhood, and the randomized greedy. The GA was not tuned.

**PECTOACO defaults as run:**
- *Parameters:* ants 20, alpha 1, beta 2, rho 0.1, p best 0.05, candidates 20, hospital candidates 5, shortlist 3, local search steps 100, optimal hospitals, sequential, 2 explorers (rate 0.05, beta 0.5, deposit 0.05).
- *Budget:* each iteration is 20 ants plus 100 local search neighbors plus 1 OptimalHospitals result, which is 121 evaluations. So 181,501 evaluations are the greedy start plus 1500 iterations, PECTOACO's tuning budget.
- *Tuning:* it was tuned on T8-T10 with seed base 20261016. T10 is shared with this experiment; the seeds are not.

**Probes before submission** (single thread, no other load, GA seed 1, not part of the results):

| instance | greedy | greedy + OptimalHospitals | RandomizedGreedy(3) | GA 20k evals | GA 181,501 evals | GA 2M evals | PECTOACO 181,501 evals (r1+r2, 10 runs) |
|---|---|---|---|---|---|---|---|
| T1 | 69,649 | 69,149 | 70,715 | 67,444 (2.5 s) | | | |
| T10 | 248,507 | 197,377 | 249,990 | 212,847 (3.3 s) | 208,655 (18 s) | 201,987 (190 s) | about 172,020 (about 1420 s each) |

- *Evaluation rates:* the GA does about 6,000-8,000 evaluations per second on one free thread. PECTOACO did about 130 per second on T8-T10 with 7 runs in parallel. Equal-evaluation GA runs therefore last well under a minute, and in 600 s the GA does tens of times more evaluations than PECTOACO.
- *Diversity in the probes:* the GA's final population diversity was 0.009 (T1, 20k evaluations), 0.005 (T10, 181,501) and 0.007 (T10, 2M). PECTOACO's final diversity in r1/r2 was about 0.11-0.17.
- *Note on the 2M-evaluation probe:* at the end the mutation rate had reached its cap of 0.75 after 67 stagnation responses.
- *Why T10 may favor PECTOACO:* greedy plus optimal hospitals alone (197,377) beats the GA after 2M evaluations (201,987). Only PECTOACO assigns hospitals optimally, on its best ant every iteration. The GA changes hospitals only through `SwapParticipants`, which keeps every hospital's load as it is.

**What the diversity numbers measure.**
- *Distance:* `PECTOSolution.distanceTo` is the share of patients whose predecessor (ambulance or previous patient) or hospital differs.
- *DIVERSITY:* the mean distance over all pairs of an evenly spaced sample of 16 members of the population an algorithm reports each iteration. The GA reports its 40 individuals; PECTOACO reports the 20 ants of the current iteration. These are different sets, so the two numbers do not mean the same thing.
- *Origin of the question:* the lab observation that prompted this experiment came from a run whose instance and budget are unknown. The lab's default instance is Table I: 1.

**How to analyze.**
- *Data:* each part has `runs.csv` (config, instance, repeat, seed, bestCost, evaluations, iterations, elapsedMs, timeToBestMs, stopReason, runtime, then the final metrics: meanCost, diversity, duplicates, stagnation, mutationRate and immigrations for the GA, branchingFactor, currentCost and explorerWins for PECTOACO) and `trace.jsonl` (one line per traced iteration with evaluations, elapsedMs, bestCost and metrics).
- *Primary analysis:* join parts 1 and 2 on instance (GA `default` vs ACO `default`), and parts 3 and 4 likewise.
- *Anytime curves:* take the best cost at fixed checkpoints from the traces, using the last trace line at or before each checkpoint. Use evaluations for parts 1 and 2, and elapsed time for parts 3 and 4.
- *Load check for the time parts:* runs are started in queue order, so the last runs of part 4 shared the machine with fewer other runs. Compare evaluations per second by start order and by instance before reading small time-based differences.
- *Interpreting part 3 against part 1:* the GA's time runs use the same seeds as `default` in part 1. A run of part 3 is the continuation of its part-1 counterpart only if the search is deterministic up to 181,501 evaluations; it is, because the GA uses only the run's RNG.

**Analysis script.** `analyze.py` in this folder implements the analysis plan and the decision rule as written above: exact Mann-Whitney U and Wilcoxon tests, Holm, A12, anytime tables at fixed checkpoints, descriptive diversity and throughput, the load check. It was written and checked against known exact p-values before any result was read. It reads the four parts' `runs.jsonl` and `trace.jsonl` (`runs.csv` exists only after a summary). Run it with `python -I -B pecto-hga-vs-aco-1-ga-evals/analyze.py` from the experiments directory; it writes `analysis.md` next to itself and works on partial results, marking the verdict as provisional.

**2026-10-26 evening: parts 5 and 6 added (ablation of every GA mechanism), code change, worker restart.**

*Why.* The user asked to know which GA mechanisms are effective and which can be removed. Part 1 ablates only SA and local search, and at 181,501 evaluations the stagnation response hardly ever triggers (0 immigrations in the probes). Parts 5 and 6 remove one mechanism at a time from the full GA, on the same instances and seeds:
- *Part 5* (`pecto-hga-vs-aco-5-ga-ablation-181k`) runs at 181,501 evaluations and keeps final results only (trace every 1000 generations).
- *Part 6* (`pecto-hga-vs-aco-6-ga-ablation-1m`) runs at 1,000,000 evaluations, about 5,000 generations, and decides. It is traced every 250 generations.
- *Why two parts:* a single 1M-evaluation part traced every 25 generations would have written about 135 MB of traces, too much for git. The configurations without SA and local search spend about 8 evaluations per generation, so they run about 130,000 generations. Part 5 also gives exact results at the early budget instead of trace approximations.
- *Design documents:* their journals hold the question, hypotheses, analysis plan and decision rule.
- *State when they were added:* part 1 was DONE and part 2 had 8 of 50 runs. No result had been read; `analyze.py` had only been run on the first 29 part-1 runs, to check that the script works.

*The 18 configurations, each one lab-parameter change from `default`:*

| group | label | change | what is removed or replaced |
|---|---|---|---|
| seeding | seedRandom | seeding = random | greedy-based initial population and migrants (all `RandomPECTOSG`) |
| selection | selRoulette | roulette = 1 | the tournament share (roulette on 1/cost only) |
| | selTournament | roulette = 0 | the roulette share (tournament of 5 only) |
| crossover | xUnbiased | cheaper route = 0.5 | ARX's preference for the cheaper route |
| | xRandomRepair | ARX repair = random | ARX's best-insertion repair (random route and position; still the nearest hospital with room) |
| | xUniform | crossover = uniform route | ARX as a whole, replaced by the framework's generic `RouteCrossover` |
| | xNone | crossover = none | recombination: the child is a copy of a random parent (`CloneCrossover`), then mutated, refined and inserted as usual |
| mutation | mMoveTrip | mutation = move trip | domain-aware `CostliestRouteMove`, replaced by the generic `MoveTrip` |
| | mNone | mutation = none | offspring mutation (rate 0, no growth) |
| | mFixedRate | mutation rate growth = 1 | the growth of the mutation rate at every stagnation |
| stagnation | noImmigration | immigration = off | migrants (perturbation and rate growth stay) |
| | noPerturbation | perturbation = off | swap-mutating the population (migrants and rate growth stay) |
| | noStagnation | stagnation period = 1,000,000 | the whole stagnation response (never triggers) |
| replacement | noAgeing | min victim age = 0 | the protection of individuals younger than 3 generations |
| refinement | noLS | local search steps = 0 | local search of the offspring |
| | noSA | SA inner loop = 0 | SA on the population's best |
| | plain | hybrid = off | both (the only combined removal) |

*Code change before parts 5 and 6* (in `metaheuristic.hybridga` and the lab's `Catalog` entry "Hybrid GA"; nothing else):
- `AlternatingRouteCrossover` got a repair switch (`bestInsertion`).
- `CloneCrossover` is new.
- `HybridGA.Builder` got `immigration(boolean)` and `perturbationOnStagnation(boolean)`.
- The Catalog entry got the lab parameters seeding, crossover, ARX repair, mutation, mutation rate growth, immigration and perturbation. Their defaults reproduce the earlier GA.

Two details make the switches clean. With perturbation off, a stagnation no longer sorts the population by age; the sort only fed the perturbation, but it changed the order parents are drawn from. At mutation rate 0, the mutation coin flips are no longer drawn. With both on (the default), the code consumes the same random numbers as before.

*Proof that the defaults are unchanged:* 20 finished part-1 runs (default, noSA, noLS and plain on all 5 instances, repeat 0) were replayed with the final code through the same path the worker uses (`ExperimentSpec` → `Catalog` → session start with the recorded seed). All 20 reproduced their recorded best costs bit for bit. PECTOACO's classes did not change.

*Worker restart.* The worker was stopped gracefully (its 7 PECTOACO runs in flight finished first, none dropped) and restarted on the new code: stopped at 20:03, restarted at 20:05 as pid 17736 on runtime `r-20261026-200519-e72c3f4748`. Part 1 and the first 17 part-2 runs ran on `r-20261026-170301-656dbd4cf3`. The remaining part-2 runs and parts 3-6 run on the new runtime. That is comparable with part 1 and with the first part-2 runs by the replay above (GA) and because the PECTOACO code is unchanged.

*Queue order after the restart:* part 2 (PECTOACO, evaluations), 3 (GA, time), 4 (PECTOACO, time), then 5 and 6 (ablation). The two time parts still run back to back.

*Analysis.* `analyze.py` covers parts 5 and 6 too: the decision table at 1M evaluations (part 6), the same table at 181,501 evaluations (part 5), mean gap per instance, and activity at the end. It also has a replication check: part 5's default, noSA, noLS and plain repeat part 1's runs exactly, so their final costs must be identical (0 differences expected).

*Planned round 2* (from the decision rule of parts 5 and 6, to be specified after part 6): remove every mechanism labeled REMOVABLE or HARMFUL together, then test the reduced GA against the default on new seeds (base 20261027) at 181,501 and 1M evaluations and at 600 s. The reduction is accepted if the upper end of the 95 % interval of the mean gap is below +0.1 %.

## Results

<!-- results:start (written by the worker; edit outside this block) -->
_Updated 2026-10-26 19:23; also in summary.md._

200 of 200 runs finished.

| configuration | n | mean cost | median | gap to default % | 95% interval | better/worse | p | Holm p | diversity | duplicates | immigrations | mutationRate |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| noSA | 50 | 105315.0501 | 95096.2133 | -0.380 | -0.559 … -0.202 | 36/14 | <0.001 | <0.001 | 0.01857 | 0.4065 | 9.280 | 0.1389 |
| plain | 50 | 105369.0851 | 94921.8392 | -0.158 | -0.413 … +0.097 | 37/13 | 0.133 | 0.266 | 0.03250 | 0.04750 | 133.2 | 0.7500 |
| default | 50 | 105916.1911 | 95483.4952 | +0.000 | +0.000 … +0.000 | 0/0 | 1.000 | 1.000 | 0.02588 | 0.3175 | 2.780 | 0.06628 |
| noLS | 50 | 105889.4031 | 95593.4559 | +0.023 | -0.119 … +0.166 | 17/33 | 0.372 | 0.372 | 0.01422 | 0.4180 | 7.020 | 0.1023 |

Gap to default by instance (%):

| configuration | C100 | T1 | T4 | T7 | T10 |
|---|---|---|---|---|---|
| noSA | -0.046 | -0.217 | -0.343 | -0.326 | -0.970 |
| plain | +1.350 | -0.413 | -0.563 | -0.607 | -0.557 |
| default | +0.000 | +0.000 | +0.000 | +0.000 | +0.000 |
| noLS | +0.200 | -0.061 | +0.111 | -0.117 | -0.016 |

Gap: (cost − default cost) / default cost of the run with the same instance and repeat; negative is better. p: exact two-sided Wilcoxon signed-rank test of the gaps; Holm p: adjusted over the configurations.
<!-- results:end -->

## Conclusions

_Not written yet: read the results against the analysis plan and the decision rule, then write here what was decided and why._
