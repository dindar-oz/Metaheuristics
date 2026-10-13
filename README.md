# Metaheuristic Algorithms in Java

A small, type-safe Java toolkit of metaheuristic algorithms with reproducible, monitorable and thread-safe runs.

## Algorithms

| Algorithm | Class | Works on |
|---|---|---|
| Evolutionary / Genetic Algorithm | `metaheuristic.ea.EA<S>` | any representation, with crossover/mutation operators |
| Simulated Annealing | `metaheuristic.sa.SA<S>` | any representation, with mutation operators |
| Local Search (stochastic hill climbing) | `metaheuristic.ls.LocalSearch<S>` | any representation, with mutation operators |
| Particle Swarm Optimization | `metaheuristic.pso.PSO` | `ContinuousProblem` |
| Differential Evolution (+ 4 boundary handlers) | `metaheuristic.de.DifferentialEvolution` | `ContinuousProblem` |
| Ant Colony Optimization (Ant System) | `metaheuristic.aco.ACO` | `GraphProblem` (e.g. TSP) |
| Deep Q-learning (constructive) | `metaheuristic.rl.dqn.DQN<S>` | `FeaturedConstructiveProblem<S>` (e.g. `ConstructiveWFLOP`) |
| Greedy construction | `metaheuristic.rl.GreedyConstruction<S>` | `FeaturedConstructiveProblem<S>` that knows its rewards |

Problems: **TSP** (`problems.tsp.TSPProblem`), **Sudoku** (`problems.sudoku.SDKProblem`), the continuous benchmarks
**Rastrigin** and **Sphere** (`problems.continuous`), the **wind farm layout optimization problem** (`problems.wflop`, below)
and **post-earthquake casualty transport** (`problems.pecto`, below).
Add your own by implementing `OptimizationProblem<S>` (minimization), `ContinuousProblem` or `GraphProblem`.

## Wind farm layout optimization (WFLOP)

`problems.wflop` implements the problem studied by Yu & Zhang (2024), *A teaching-learning-based optimization
algorithm with reinforcement learning to address wind farm layout optimization problem*, Applied Soft Computing 151,
111135, with the Jensen wake model of Katić, Højstrup & Jensen (1987) and the wind farm of Mosetti et al. (1994):

- a 2 km × 2 km farm divided into 10 × 10 cells of 200 m, a turbine at the center of each occupied cell;
- turbines: hub height 60 m, rotor radius 20 m, C_T = 0.88, efficiency 40 %, air density 1.225, roughness 0.3 m;
- Jensen wake: radius r + αx with α = 0.5 / ln(h / z0), deficit (1 − √(1 − C_T)) (r / rₓ)², multiple wakes combined
  by the root of the sum of squares; *full wake* (Location 1) or deficit scaled by the covered rotor area
  (*partial wake*, Location 2);
- objective: minimize cost / power with cost = N (2/3 + 1/3 e^(−0.00174 N²)) and power = Σ ½ η ρ A v³;
- `WFLOPCase.CASE_1` … `CASE_4` (1–100 or 39 turbines × full/partial wake) with `WindScenario.condition1()`
  (12 m/s, 36 directions) or `condition2()` (8/12/17 m/s by direction sector), using the paper's continuous
  encodings (Figs. 9 and 10), so DE and PSO solve them directly.

Besides the paper's real-vector encodings (for DE and PSO), `DiscreteWFLOP` works on layouts directly: a solution is
the set of occupied cells (`CellLayout`), with moves that relocate a turbine (anywhere or to a neighboring cell), add
or remove one (Cases 1–2 only) and a crossover that keeps the cells both parents share. EA, SA and local search run on
it. The objective is the same, so results are directly comparable; under the paper's budget of 2500 evaluations the
discrete SA and local search reach better layouts in all four cases than any algorithm reported in the paper.
In the lab, the wind farm problem offers all five algorithms in one session, each on its native encoding.

Evaluation is fast because the cell-to-cell wake effects are computed once per farm, as a sparse matrix: only about 5%
of the (upstream cell, downstream cell, wind direction) triples have a wake that reaches the rotor, so a layout is
evaluated from those alone. A `CellLayout` keeps its evaluation, and a neighbor derived from it by a move, addition or
removal recomputes only the terms of turbines in the wakes of the changed cells, about 9% of the terms. Terms are summed
in fixed point, so an incremental evaluation equals a full one bit for bit and no rounding drift builds up over long
runs. Compared with summing a dense table over all turbine pairs, a full evaluation is about 5× faster (DE and PSO
benefit too) and evaluating a neighbor in SA or local search about 10–25× faster.

The implementation reproduces the powers, costs and efficiencies the paper reports for its best layouts in all four
cases (see `WFLOPTest`). Where the paper's printed equations differ from what reproduces its results, the code follows
the cited original model: the wake *radius* grows by αx (the paper prints 2αx, the growth of the diameter), and in
full-wake mode a turbine is waked as soon as the wake circle reaches its rotor.

```bash
./mvnw compile exec:java -Dexec.mainClass=examples.WFLOPExample        # DE, PSO, EA, SA, LS; paper protocol, Condition 1
./mvnw compile exec:java -Dexec.mainClass=examples.WFLOPExample -Dexec.args=2   # Condition 2
```

### Deep Q-learning as a constructive metaheuristic

`DQN` (package `metaheuristic.rl`) treats a problem as an episodic decision process (`ConstructiveProblem`): an episode
builds one solution decision by decision, each episode costs one evaluation, and the result is the best solution of all
episodes. `ConstructiveWFLOP` (package `problems.wflop.rl`) places turbines one at a time on an empty farm, or stops
(Cases 1–2). The rewards telescope to −objective / objective of one unwaked turbine, so maximizing the return is
minimizing the objective exactly.

- **Features.** The network scores every free cell from 12 features (`LayoutFeature`) computed from the farm's
  `WakeMatrix` and kept up to date as turbines are placed: the power the turbine would get, the power it would take from
  the others, marginal power vs. marginal cost (the objective improves exactly when the first exceeds the second), the
  wakes it would receive from and cast on the free cells, the distance to the edge, and the progress and efficiency of
  the layout. All are intensive (per turbine or per cell), so they mean the same on farms of any size.
- **Learning.** Experience replay with n-step returns, a target network, double Q-learning targets over a shortlist of
  the best-valued actions, elite replay of the best episode, Huber loss and Adam, on a small hand-written MLP
  (`metaheuristic.rl.nn`). Exploration is softmax over standardized values (default) or epsilon-greedy.
- **Reward baseline.** The reward of every action is known before acting, so Q(s, a) = reward(s, a) + f(s, a) / turbines:
  the network learns only the foresight (what an action does to the turbines placed later), per turbine. An untrained
  network is exactly the greedy heuristic (`GreedyConstruction`), which is also the first episode of every run.
- **Transfer.** A run can start from a saved network (`Mlp.save`/`load`), keep training it or only use it
  (`training(false)`), and hand its final network over (`trainedNetwork`).

`examples.rl.WFLOPTransferExample` trains on the paper's 10 × 10 farm (39 turbines) and uses the network on 15 × 15 and
20 × 20 farms with the same turbine density. Mean objective of 3 runs of 300 evaluations (Condition 1, full wake):

| | 15 × 15, 88 turbines | 20 × 20, 156 turbines |
|---|---|---|
| Greedy heuristic (1 evaluation) | 0.00143910 | 0.00145581 |
| SA, 300 evaluations | 0.00145235 | 0.00148248 |
| DQN from scratch | 0.00143803 | 0.00145429 |
| DQN from the 10 × 10 network, still learning | 0.00143969 | 0.00145372 |
| DQN from the 10 × 10 network, frozen | **0.00143748** | 0.00145464 |
| SA, 30 000 evaluations (1–1.5 s, other seeds) | **0.00143257** | **0.00144980** |

With the same number of evaluations, every DQN variant beats SA by far on the large farms, and the frozen 10 × 10
network, which never saw a larger farm, builds better 15 × 15 layouts than learning from scratch. But DQN takes
30–180 s per run (it trains a network, and its step rewards come from incremental evaluations that are not counted),
while SA takes a fraction of a second: with comparable time SA is clearly better. On the 10 × 10 farm, DQN reaches
about the same objectives as SA with the paper's budget of 2500 evaluations, slightly worse on average.

```bash
./mvnw compile exec:java -Dexec.mainClass=examples.rl.WFLOPDQNExample        # DQN vs SA and LS, paper protocol
./mvnw compile exec:java -Dexec.mainClass=examples.rl.WFLOPTransferExample   # 10 x 10 -> 15 x 15, 20 x 20
```

## Post-earthquake casualty transport optimization (PECTO)

`problems.pecto` implements the problem of Balcı & Öz, *Post-Earthquake Casualty Transport Optimization*:
ambulances take patients, one at a time, to hospitals of limited capacity.

- Participants are numbered ambulances `0..A-1`, patients `A..A+P-1`, hospitals `A+P..A+P+H-1` (Eq. 4). A solution
  (`PECTOSolution`) gives every ambulance a `Route` of trips, each to a patient and then to a hospital; the paper's
  flat encoding (`0 7 13 10 12 1 5 13 ...`) is available through `toSequence()` / `PECTOProblem.decode`.
- Every leg costs its Euclidean length times the urgency weight of its patient (stable 1.0 … critical 0.2); the
  objective is the sum over all routes. A solution is feasible when every patient is transported exactly once and no
  hospital exceeds its capacity (`isFeasible`).
- `PECTOInstances.paper(k, seed)` generates instance k of Table I (200 ambulances, 64 hospitals of 81 beds,
  2430–5163 patients in a 350 × 350 area); urgency levels are drawn uniformly, as the paper does not state them.
- Solutions are immutable and share unchanged routes; route costs and hospital loads are cached, so a neighbor that
  changes two routes is evaluated in time proportional to those routes (about 1 µs on Table I instances), with
  exactly the same result as a full evaluation. Distances are computed from coordinates instead of a 235 MB matrix.

`problems.pecto.operators` has simple feasibility-preserving operators for EA, SA and local search: `MoveTrip`,
`ChangeHospital`, a uniform `RouteCrossover` with repair, and random initial solutions (`RandomPECTOSG`). They are
not the paper's operators. `PECTOGraph` views the problem as a `GraphProblem` for ACO: an ant builds a giant tour over
the ambulances and patients, which is cut into routes at the ambulances, and every patient goes to the free hospital
that minimizes its delivery plus the next pickup, so every tour is feasible. ACO keeps node × node matrices, so it
is practical on custom instances of a few hundred patients but slow on Table I sizes. In the lab, `PECTOVisualizer`
draws the area with the routes and lists the route costs.

## Metaheuristic Lab (GUI)

```bash
./mvnw compile exec:java        # mvnw.cmd on Windows
```

A desktop tool to run several algorithms side by side and watch them converge:

- pick a problem (TSP, Sudoku, wind farm layout, casualty transport, Rastrigin, Sphere) and edit its parameters;
- add any number of algorithm runs (also several configurations of the same algorithm) and edit their parameters;
- set the budget (max evaluations, time limit, target cost) and a seed; every run is reproducible from its seed;
- **Run** starts all runs in parallel; the *Convergence* tab plots the exact best-cost curve of every run
  (against evaluations or time, optionally log-scale), the *Metrics* tab plots algorithm internals such as
  SA's temperature and current cost or the population mean cost, and the results table shows live status;
- **View solution** (or double-click a row of the results table) shows the best solution of the selected run: the
  final one, or the best so far while it is still running. The wind farm problem draws the layout, colors every turbine
  by its power relative to an unwaked turbine and, for a single wind direction, draws the wakes. The TSP draws the
  tour with the cities placed by multidimensional scaling of the distance matrix, so drawn distances are proportional
  to the matrix (exactly for planar instances; otherwise the remaining distortion is reported). Other problems show
  the solution as text;
- **Stop** cancels the runs, **Export CSV** saves all convergence curves.

Runs execute on a thread pool and only push their improvements to lock-free queues; the UI drains them with a
Swing timer, so monitoring never slows the search down. Problems and algorithms offered by the lab are declared in
`lab.model.Catalog`: adding an entry there is all it takes to make a new problem or algorithm available. A problem can
also register a `lab.view.SolutionVisualizer` (a function from problem and solution to a Swing component), as the
wind farm and TSP problems do with `WFLOPVisualizer` and `TSPVisualizer`.

## Quick start (API)

```java
TSPProblem tsp = TSPProblem.randomEuclidean(50, 42);
List<MutationOperator<IntegerPermutation>> ops = Arrays.asList(new TwoOpt(), new RandomRemoveReinsert<>());

SA<IntegerPermutation> sa = SA.builder(ops, new RandomPermutationSG(50))
        .cooling(new GeometricCooling(0.995))
        .innerLoopLength(20)
        .build();                                   // immutable, validated configuration

RunResult<IntegerPermutation> result = Runner.of(sa)
        .seed(42)                                   // same seed => identical run
        .stopWhen(new EvaluationBasedTC(50_000))    // exact: never more than 50 000 evaluations
        .listener(Listeners.throttled(Listeners.console(), 1000))
        .run(tsp);

IntegerPermutation best = result.bestSolution();    // typed, no casts
System.out.println(result);                         // algorithm + parameters, seed, cost, evaluations, stop reason
```

Comparing algorithms (runs in parallel, results independent of the thread count):

```java
Experiment.of(ExperimentCase.of("SA", sa, tsp), ExperimentCase.of("ACO", ACO.builder().build(), tsp))
        .repeats(10).seed(42).threads(8)
        .stopWhen(new EvaluationBasedTC(50_000))
        .handler(new ConsoleERH())                               // best/mean/std per case
        .handler(new CsvERH(Paths.get("results", "tsp.csv")))   // one row per run incl. seed and parameters
        .run();
```

## Design

- **Algorithms are immutable configuration.** Everything a run changes (RNG, counters, population, pheromone, ...)
  lives in `search()`'s locals and in its `SearchContext`, so one instance can be run any number of times,
  also concurrently. Algorithms are created with builders that validate parameters and `describe()` themselves.
- **Reproducibility.** Every run owns a seeded RNG (`SearchContext.rng()`); there is no global randomness.
  Operators, selectors and generators receive the RNG as a parameter. Unseeded runs pick a random seed and report it.
  `Experiment` derives the seed of repeat *r* of case *c* from its base seed, so results do not depend on scheduling.
- **Exact budgets.** `SearchContext.evaluate()` counts evaluations, tracks the best solution and refuses to evaluate
  once the stop condition holds. Stop conditions (`metaheuristic.termination`) are stateless functions of `Progress`,
  combinable with `TerminalCondition.anyOf/allOf`.
- **Monitoring.** `SearchListener` receives `onStart`, `onProgress` (every iteration, with algorithm-specific metrics
  such as `temperature` or `meanCost`), `onImprovement` and `onFinish` with immutable snapshots.
  `Listeners.throttled(...)` limits the rate, `Listeners.async(...)` moves slow listeners (files, GUI) off the search
  thread, and `Runner.listenerPerRun(...)` creates per-run listeners such as CSV traces.
- **Control from other threads.** `Runner.start(problem, executor)` returns a thread-safe `RunHandle`:
  `latestProgress()`, `cancel()` and `result()` (a `CompletableFuture`). The Metaheuristic Lab builds on it.
- **Extensible EA.** Parent selection, crossover strategy, mutation strategy and `ReplacementStrategy`
  (steady state, mu+lambda) are pluggable; per-generation behaviour is added with `GenerationHook`s
  (`ImmigrationHook`, `AgeingHook`, `LocalSearchHook`).
- **Type safety.** `Representation<S>`, `OptimizationProblem<S>`, operators and algorithms are generic, so e.g. giving
  PSO a TSP instance is a compile error. Operators must not modify their inputs (enforced by the test suite).

## Project layout

```
src/main/java/
  base/            OptimizationProblem, ContinuousProblem, MutationOperator, InitialSolutionGenerator
  metaheuristic/   Metaheuristic, AbstractMetaheuristic, Params and the algorithms (ea, sa, ls, pso, de, aco)
  metaheuristic/run/          Runner, RunHandle, SearchContext, Progress/ProgressSnapshot, RunResult, SearchListener
  metaheuristic/termination/  evaluation, iteration, time, target-cost and stagnation stop conditions
  metaheuristic/listener/     console and CSV listeners, throttled and async wrappers
  operators/       generic mutation operators for array/permutation representations (2-opt, swap, insert, ...)
  representation/  IntegerPermutation, IntegerVector, DoubleVector, IntegerMatrix, BinaryString, Individual, Population
  experiments/     Experiment runner and result handlers
  lab/             Metaheuristic Lab desktop tool (Swing + XChart + FlatLaf); lab/model/Catalog lists its problems
  problems/        TSP, Sudoku, wind farm layout (wflop) and continuous benchmarks (Rastrigin, Sphere)
  examples/        runnable demos
src/test/java/     reproducibility, exact budget, concurrency, cancellation and operator/selector contract tests
```

## Building and running

Requires Java 8+. The Maven wrapper downloads Maven and the dependencies (commons-math3, XChart, FlatLaf, JUnit):

```bash
./mvnw test                                                              # build and run the tests
./mvnw compile exec:java                                                 # Metaheuristic Lab
./mvnw compile exec:java -Dexec.mainClass=examples.TSPExample           # TSP: EA vs SA vs LocalSearch vs ACO
./mvnw compile exec:java -Dexec.mainClass=examples.ContinuousExample    # DE boundary handlers vs PSO on Rastrigin
./mvnw compile exec:java -Dexec.mainClass=examples.SudokuExample        # EA and SA on a sudoku
./mvnw compile exec:java -Dexec.mainClass=examples.MonitoringExample    # listeners, traces, polling and cancelling
```

On Windows use `mvnw.cmd` instead of `./mvnw`.

## License

This project is licensed under the [MIT License](LICENSE).
