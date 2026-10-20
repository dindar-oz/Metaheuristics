# 5. PECTOACO: the MAX-MIN ant colony for PECTO

`problems.pecto.aco.PECTOACO` is a **MAX-MIN Ant System** (Stützle & Hoos, 2000) that builds PECTO routes
directly. Every iteration, its best ant is improved by a short local search and then given the optimal hospitals for
its routes. Against early convergence, a few **explorer ants** sometimes ignore the pheromone. It is the main
algorithm for PECTO in this project, and its explorer defaults come from the tuning experiments in `experiments/`.

Read [The PECTO problem](04-pecto-problem.md) first. Section 4.2 explains the properties of the objective that the
algorithm relies on.

| File | Role | Lines |
|---|---|---|
| `PECTOACO.java` | Parameters (builder), the iteration loop, local search, pheromone update, metrics | ~450 |
| `CandidateLists.java` | What the ants know about the instance: nearest patients, orders by distance | ~100 |
| `Pheromone.java` | Pheromone values, bounds, evaporation/deposit/clamp, branching factor | ~150 |
| `Ant.java` | Construction of one solution | ~320 |
| `AntBuilds.java` | Sequential or parallel construction of the ants of an iteration | ~120 |

Read them in this order, with this document open.

## 5.1 Why build routes directly?

The generic `ACO` can solve PECTO through `PECTOGraph`: ants build a "giant tour" over ambulances and patients,
which is then cut into routes, and hospitals are chosen by a fixed rule. That approach has two problems:

- It keeps full node × node matrices: 170–690 MB on Table I, and slow.
- The pheromone and the heuristic do not see the real cost structure. The hospital choice is not learned, and the
  urgency-weighted pickup leg starts from a hospital, not from the previous patient.

PECTOACO instead:

- moves **one ambulance at a time**, so routes grow in parallel the way a dispatcher would assign them;
- learns two kinds of choices with pheromone: **which patient follows which** (`τ(prev, q)`) and **which hospital a
  patient goes to** (`τ(q, h)`);
- uses **candidate lists** (20 nearest patients, 5 nearest hospitals with a free bed), so pheromone memory is
  `(A + P) × 20 + P × H` values. On Table I: 10 that is about 440 000 doubles (twice that with the per-iteration
  weights), a few MB;
- builds **only feasible solutions**.

On Table I: 1 an ant takes about 20 ms. PECTOACO improves on the greedy solution within about 75 iterations, while
the giant-tour ACO is still above the greedy cost after 2 000 evaluations.

## 5.2 The run, from start to finish

```text
search(problem, ctx):
    greedy ← GreedyPECTOSG.complete(empty solution)
    best   ← (greedy, ctx.evaluate(greedy))                  # evaluation #1
    lists  ← CandidateLists(problem, candidates)
    pheromone.bound(best.cost); pheromone.fill()              # every τ = τ_max of the greedy cost
    ants   ← antCount ants, each with its own RNG derived from the run's RNG;
             the last `explorers` ants are explorers
    for iteration = 0, 1, 2, ... until ctx.shouldStop():
        pheromone.weigh()                                     # τ^α for this iteration, read-only for the ants
        build every ant's solution (sequentially or in parallel)
        evaluate them on the run's thread, in ant order       # antCount evaluations
        iterBest ← improve(best ant of the iteration)         # ≤ localSearchSteps + 1 evaluations
        if iterBest < best: best ← iterBest; pheromone.bound(best.cost)
        updatePheromone(iteration, iterBest, best, best explorer)
        ctx.iterationCompleted(metrics, ants' solutions)
```

**Evaluation budget arithmetic.** With the defaults, one iteration costs 20 (ants) + 100 (local search) + 1 (optimal
hospitals, evaluated whenever they differ, which is nearly always) = **121 evaluations**. With the initial greedy
solution: *N* iterations ≈ `1 + 121·N`. 600 iterations = 72 601 and 1 500 iterations = 181 501 evaluations. These are
the budgets of the tuning experiments. Every solution, including every local search neighbor, goes through
`ctx.evaluate`, so comparisons with other algorithms at equal evaluations are fair. Note, however, that a neighbor
evaluation costs about 1 µs while an ant construction costs about 20 ms.

## 5.3 Candidate lists (`CandidateLists`)

Computed once per run and only read afterwards:

| Field | Content |
|---|---|
| `patients[n]` | For every node *n* (an ambulance or a patient): its `candidates` nearest patients by **urgency-weighted** distance `w_q · D(n, q)`, nearest first |
| `candidateOf[k]` | For patient index *k*: the nodes whose lists contain it. Used to count each node's remaining unserved candidates when *k* is served |
| `byDistance[l]` | For every location an ambulance can wait at (its start position or a hospital): **all** patients by weighted distance. Used as a fallback when a node's candidates are all served |
| `hospitalOrder[k]` | For patient index *k*: all hospitals by distance from the patient |

Why candidate lists? With thousands of patients, a roulette over all of them would give most of the probability to
the many patients that are neither near nor far, and the ants would build poor solutions. The same holds for 64
hospitals.

## 5.4 Pheromone (`Pheromone`)

Two pheromone structures:

- **Patient pheromone** `τ(prev, q)`: directed, for `q` in the candidate list of `prev`. Here `prev` is an ambulance
  (for its first trip) or the previous patient of the route. It learns *which patient follows which*.
- **Hospital pheromone** `τ(q, h)`: a full `P × H` matrix. It learns *which hospital a patient goes to*.

**Bounds (MAX-MIN).** All values are kept in `[τ_min, τ_max]`:

```
τ_max = 1 / (ρ · bestCost)                                  # updated whenever the best cost improves
root  = pBest^(1/P)                                         # P = number of patients (decisions per ant)
avg   = max(2, choices / 2)                                 # choices = candidates (patients) or hospitalCandidates
τ_min = max( τ_max · (1 − root) / ((avg − 1) · root),  tauMinRatio · τ_max ),  at most τ_max
```

Patients and hospitals have separate `τ_min` values. On large instances the pBest formula gives a **tiny** `τ_min`.
For P ≈ 5 000 and pBest = 0.05, `τ_min/τ_max` ≈ 7·10⁻⁵ for patients and ≈ 4·10⁻⁴ for hospitals, so choices that lose
pheromone become practically impossible. `tauMinRatio` sets a floor (off by default). Explorer ants (5.7) turned out
to be the better remedy.

**Initialization:** every value is `τ_max` of the greedy cost, so the first iterations are driven by the heuristic.

**Per iteration:**

1. `weigh()`: compute `τ^α` for all entries. The ants read only these weights, which do not change while they build.
2. After the ants and the improvement step, `updatePheromone`:
   - `evaporate()`: every value × `(1 − ρ)`;
   - `deposit(solution, 1/cost)` by the **iteration best** (after improvement), or by the **best so far** on every
     5th iteration (`GLOBAL_BEST_PERIOD`, iterations 4, 9, 14, ...). A deposit adds to `τ(prev, q)` for every
     consecutive pair of a route (if `q` is a candidate of `prev`) and to `τ(q, h)` for every trip;
   - the best **explorer** of the iteration additionally deposits `explorerDeposit / cost`, unless its solution is the
     one that deposited anyway;
   - `clamp()` back into `[τ_min, τ_max]`.

**λ-branching factor** (Gambardella & Dorigo), reported as the metric `branchingFactor`: the mean number, per node,
of candidate successors whose patient pheromone is at least `τ_min + 0.05 · (τ_max − τ_min)`. It is about
`candidates` (20) at the start and about 1 when the colony has converged on one successor per node. In the 1 500-
iteration runs it ends at about 0.9–1.1: the colony converges, and the explorers keep slightly more alternatives
open (diversity 0.145 vs 0.113 for the plain colony).

## 5.5 Building one solution (`Ant.construct`)

State while building: which patients are served, the free beds of each hospital, and per ambulance: `previous`
(itself or its last patient), `at` (its start position or the hospital where it waits), `pending` (its last patient,
whose hospital is still provisional), the trips fixed so far.

```text
reset: every ambulance at its start, no trips; every patient unserved; every bed free
repeat P times:                       # one patient per step
    a ← chooseAmbulance()
    q ← choosePatient(a)
    pickUp(a, q)                      # fixes the hospital of a's pending patient, gives q a provisional one
finally: every pending trip keeps its provisional hospital (it has no next patient)
```

### Step 1: choose the ambulance

Every ambulance is **rated** by its most attractive next patient:

```
rating(a) = max over unserved candidates c of previous(a) of   τ^α(previous(a), c) · η(c)^β
η(c)      = 1 / (w_c · D(at(a), c))          # urgency-weighted distance from where the ambulance is now
```

If all of a node's candidates are served, the rating uses the nearest unserved patient (from `byDistance`) at
`τ_min^α`. The moving ambulance is drawn **uniformly from the `shortlist` (3) best rated**. Ambulances far from the
remaining patients are rarely drawn, so they end up with few trips or none (property 4 in 4.2).

Performance trick: while an ambulance stays where it is, serving other patients can only lower its rating (fewer
options, pheromone fixed). A stale rating is therefore an **upper bound**, and it is recomputed only if it could
still make the shortlist. That bound breaks when a node's last candidate is served, because the fallback patient may
be nearer. The `EXACT` / `BOUND` / `UNKNOWN` states in `Ant` handle this.

### Step 2: choose the patient

Roulette over the **unserved candidates of `previous(a)`**, with probability ∝ `τ^α(previous(a), q) · η(q)^β`,
where η is again measured from the hospital where the ambulance waits. If no candidate is left, the ambulance takes
the patient it was rated by (the nearest unserved one).

Note the two different reference points. The pheromone and the candidate list are keyed on the **previous patient**
(a stable identity across iterations, which the colony can learn from). The heuristic uses the ambulance's actual
position, the **hospital** it waits at, because that is where the pickup leg starts (Eq. 8).

### Step 3: choose the hospital, twice

By property 2 in 4.2, a patient's hospital affects its delivery leg and the next pickup leg, and the next patient is
not known at pickup time. So the hospital is drawn twice:

1. **On pickup** (provisional): among the `hospitalCandidates` (5) nearest hospitals **with a free bed**, with
   probability ∝ `τ^α(q, h) · (1 / (w_q · D(q, h)))^β`. The bed is taken and the ambulance "waits" there (`at(a) = h`).
2. **When the ambulance's next patient r is chosen**: the provisional bed is released and the hospital is drawn
   again with the full two-leg cost `η = 1 / (w_q · D(q, h) + w_r · D(h, r))`. Then the trip is fixed.

The last trip of every route keeps its provisional hospital, which is correct because it has no next pickup.

**Feasibility is guaranteed:** every patient is served exactly once (the `served` flags), and a hospital is only
drawn while it has a free bed. A free bed always exists, because the total capacity is at least *P* and every patient
holds at most one bed at a time.

### Randomness and the exponent shortcut

Each ant draws only from its own RNG. `Ant.eta(cost, b)` avoids `Math.pow` for the usual exponents (2, 1, 0.5, 0),
because it is the innermost computation.

## 5.6 Improving the iteration best

`improve(best ant)`:

1. **Local search**, `localSearchSteps` (100) steps. Each step applies a random `MoveTrip` or `SwapTrips` to the current
   solution and evaluates it; it is kept if strictly better. Both moves keep hospital loads, so feasibility is
   preserved. A neighbor changes at most two routes, so it costs about 1 µs to evaluate.
2. **Optimal hospitals** (if `optimalHospitals`, default on): `OptimalHospitals.of(solution)` gives the best hospitals
   for the final routes (see [4.5](04-pecto-problem.md#45-optimalhospitals-the-exact-hospital-assignment)). The
   result is evaluated and kept if not worse.

The improved solution then deposits pheromone. The ants learn from improved solutions, but they still build their
own solutions from scratch.

## 5.7 Explorer ants

**Problem.** On large instances the colony converges: the branching factor drops to about 1, and the ants rebuild
nearly the same solution.

**Mechanism.** The last `explorers` (2) ants of every iteration are *explorers*. Each of their patient and hospital
draws, independently with probability `explorerRate` (0.05), **ignores the pheromone**: every candidate gets
pheromone weight 1 and the heuristic exponent is `explorerBeta` (0.5, flatter than β = 2, so the draw spreads over
the candidates). The other 95 % of their draws are ordinary. An explorer's solution therefore stays close to what the
colony has learned, so it can be good, but it differs in a few places. The **best explorer** of each iteration
deposits `explorerDeposit` (0.05) × `1/cost`, which keeps alternatives in the pheromone alive without letting the
explorers steer the colony.

**How the design was found** (see `pecto ressearch/PECTOACO Diversity Sensitivity.pdf`, which uses older parameter
names, and the two tuning journals):

- Restarting the pheromone when the branching factor drops: never helped.
- *Fully* pheromone-free ants: their solutions were 38–81 % worse than the colony's, so they never deposited and had
  no effect.
- Local search on the best explorer: neutral at 25 steps, harmful at 100 (it costs evaluations).
- **Partial** exploration (5 % of draws) with a **small deposit**: a significant gain. This is the current design.
- Two tuning rounds at 1 500 iterations on T8–T10 (30 paired runs per setting) kept the defaults:
  **−0.185 %** cost against the plain colony (`explorers(0)`), 95 % interval [−0.27, −0.10] %, p = 0.0001.
  `explorerBeta = 2` was nominally best (−0.192 %), but not significantly (p = 0.64 against the default). A rate of
  0.02, or a deposit of 0.02, is significantly worse.
- The gain is **instance-dependent**: about −0.42 % on T9, −0.13 % on T10, and none on T8.

The metric `explorerWins` counts the iterations whose best ant was an explorer. It is usually near 0 with the
defaults, so explorers work mainly **through their deposits**, by keeping alternatives open, rather than by finding
the best solution themselves.

## 5.8 Parameters

The *catalog name* is what you use in the Metaheuristic Lab and in experiment specs.

| Builder method | Catalog name | Default | Range | Meaning |
|---|---|---|---|---|
| `antCount` | `ants` | 20 | ≥ 1 | Ants per iteration |
| `alpha` | `alpha` | 1 | ≥ 0 | Pheromone influence |
| `beta` | `beta` | 2 | ≥ 0 | Heuristic influence |
| `rho` | `rho` | 0.1 | (0, 1] | Evaporation rate |
| `pBest` | `p best` | 0.05 | (0, 1) | Sets τ_min: probability of building the best solution once converged |
| `candidates` | `candidates` | 20 | ≥ 1 | Nearest patients considered after each stop |
| `hospitalCandidates` | `hospital candidates` | 5 | ≥ 1 | Nearest hospitals with a free bed considered for a patient |
| `shortlist` | `shortlist` | 3 | ≥ 1 | Best rated ambulances among which the moving one is drawn (1 = always the best) |
| `localSearchSteps` | `local search steps` | 100 | ≥ 0 | Neighbors tried on the iteration best (0 = no local search) |
| `optimalHospitals` | `hospitals` | `optimal` | `optimal` / `as built` | Give the iteration best the optimal hospitals for its routes |
| `executionMode` | `execution` | `sequential` | `sequential` / `synchronous` | Build the ants one by one, or in parallel. **Same result.** |
| `threads` | `threads` | #cores | ≥ 1 | Threads in synchronous mode |
| `tauMinRatio` | `tau min ratio` | 0 | [0, 1) | Floor of τ_min as a fraction of τ_max (0 = pBest formula only) |
| `explorers` | `explorers` | 2 | 0 … ants | Explorer ants per iteration (0 = plain MMAS) |
| `explorerRate` | `explorer rate` | 0.05 | [0, 1] | Probability that an explorer's draw ignores the pheromone |
| `explorerBeta` | `explorer beta` | 0.5 | ≥ 0 | Heuristic exponent in those draws (0 = uniform over the candidates) |
| `explorerDeposit` | `explorer deposit` | 0.05 | ≥ 0 | Deposit of the best explorer as a fraction of `1/cost` (0 = none) |

Fixed constants: `GLOBAL_BEST_PERIOD = 5` (every 5th iteration the best-so-far deposits), λ = 0.05 for the branching
factor, `MIN_COST = 1e-10` (guards divisions).

Parameters are validated in the constructor (`Params.*`), so an invalid value fails at `build()` with a readable
message. `describe()` lists them all and is stored with every run.

## 5.9 Execution modes and performance

- `SEQUENTIAL`: the ants are built one after the other on the run's thread.
- `SYNCHRONOUS`: at the start of each iteration, all ants are built in parallel on `threads` daemon threads
  (`AntBuilds.Synchronous`, `invokeAll`).

**Both give exactly the same run for the same seed, for any thread count.** This holds because:

- every ant has its **own RNG**: `MersenneTwisterRNG(Seeds.derive(antSeed, antIndex))`, with `antSeed` drawn once
  from the run's RNG;
- during construction the ants only **read** shared data (candidate lists, pheromone weights);
- the ant computes its solution's cost while building, which caches the route costs. Evaluation, local search and
  pheromone update then happen on the run's thread, **in ant order**.

When to use which: for a single run in the lab, synchronous mode uses all cores. In experiments the worker already
runs several runs in parallel (one per core), so use sequential mode (the default) there.

Speed: about 20 ms per ant on Table I: 1, and about 0.7–1.2 s per iteration on T8–T10 sequentially. A 1 500-iteration run on
T8–T10 took 17–31 minutes. `PECTOACOBenchmark` (test sources) measures wall time:
`java -cp <classes> problems.pecto.aco.PECTOACOBenchmark 10 20 5` runs 5 runs of 20 iterations on Table I: 10 and
prints the median.

## 5.10 Metrics reported every iteration

| Metric | Meaning |
|---|---|
| `meanCost` | Mean cost of the ants' solutions (before improvement) |
| `currentCost` | Cost of the iteration best after local search and optimal hospitals |
| `branchingFactor` | λ-branching factor of the patient pheromone (5.4) |
| `explorerWins` | Iterations so far whose best ant was an explorer (only if `explorers > 0`) |
| `stagnation`, `diversity`, `duplicates` | Added by the run when diagnostics are on (always in the lab and the experiment runner). `diversity` is the mean `PECTOSolution.distanceTo` between the ants' solutions. |

The experiment runner stores the last values per run (`runs.jsonl`) and the values every `traceEvery` iterations
(`trace.jsonl`).

## 5.11 Changing PECTOACO safely

Experiments compare runs made at different times, sometimes with different builds. These rules keep the results
valid.

1. **Decide whether your change should alter the search.** A refactoring or a speed-up must not. A new idea will.
2. **Run the golden master**: `./mvnw test -Dtest=PECTOACOGoldenTest`. It runs 12 fixed cases (defaults, plain colony,
   explorers in both execution modes, unusual exponents, no local search, scarce beds, tiny instances, a Table I
   instance) and compares a fingerprint of the best cost, the best solution, the evaluation count and every
   iteration's metrics, bit for bit.
   - Refactoring or speed-up: it must still pass. This is how the split of `PECTOACO` into
     `CandidateLists`/`Pheromone`/`Ant`/`AntBuilds` was checked in the middle of tuning round 2.
   - Intended change of the search: it fails, and the failure message lists the new fingerprints. Paste them in,
     and note that **results from before the change are no longer comparable**. A new experiment must rerun its
     baseline with the new code.
3. **Keep determinism:**
   - Draw random numbers only from the ant's RNG during construction and from `ctx.rng()` elsewhere. Never use
     `Math.random`, `new Random()` or `ThreadLocalRandom`.
   - Never let the order of a `HashMap`/`HashSet` affect a decision.
   - Keep evaluation, local search and pheromone updates on the run's thread, in ant order.
   - Ants must not write shared state while building.
   - Check with `PECTOACOTest.sameSeedSameRun` and `synchronousRunIsTheSequentialRun`.
4. **Evaluate everything through `ctx.evaluate`.** Any solution the algorithm "uses" must be counted, or
   comparisons at equal evaluations become unfair.
5. **Keep every ant feasible** (`PECTOACOTest.antsBuildFeasibleSolutionsWhenEveryBedIsNeeded`,
   `explorersBuildFeasibleSolutions`).
6. **A new parameter** needs: a builder field with a default, validation in the constructor, an entry in
   `parameters()` (so `describe()` records it), a `ParamSpec` in `Catalog.pectoAco()` (so the lab and specs can set
   it), tests, and a golden case if it adds a new branch. Choose the default so that existing behavior is unchanged,
   or record new fingerprints deliberately.
7. **Measure speed** with `PECTOACOBenchmark` before and after. The same seed gives the same cost, so only time
   differs.
8. **Restarting the experiment worker** picks up your new code. If an experiment is half done, either make sure the
   search is unchanged (golden master) and write it in the journal's Notes, or let the experiment finish first.

## 5.12 Open questions

From the conclusions of the tuning journals, plus what the code suggests:

- **Why does T8 not benefit from explorers?** All Table I instances have 5 184 beds. T8 has 628 spare beds, T9 324
  and T10 only 21 (see [4.6](04-pecto-problem.md#46-instances)). Capacity slack, and the spatial spread of patients,
  are the first things to check. Instance seeds other than 42 would also tell whether this is about T8 in particular.
- **Budget and size:** does the explorer gain hold at 300–600 iterations, or on Table I: 1–5?
- **Against other algorithms:** tuned PECTOACO against SA, EA and local search (and, e.g., parallel SA or
  ruin-and-recreate) at **equal evaluations** and at **equal wall time**. Report both: an ant evaluation costs
  about 20 ms, a local search neighbor about 1 µs.
- **Parameters never tuned on large instances:** `candidates`, `hospitalCandidates`, `shortlist`,
  `localSearchSteps`, `rho`, the deposit schedule (`GLOBAL_BEST_PERIOD`). Each one is a well-defined experiment for the
  runner.
- **The objective has no time dimension** ([4.2](04-pecto-problem.md#42-properties-of-the-objective-worth-knowing)).
  Any study of service order or waiting times needs a different objective, i.e. a model change, not an algorithm
  change.

Before you start any of these, write the spec and the journal fields (question, hypothesis, analysis plan, decision
rule) as described in [Experiments 3.7](03-experiments.md#37-journals-plan-before-you-look), and discuss them with
your supervisor.
