# Developer documentation

This folder is the onboarding guide for people who will work on the project. It covers the architecture, the tools,
how experiments are run and recorded, and in depth one problem and one algorithm: **PECTO** (post-earthquake
casualty transport) and **PECTOACO** (the MAX-MIN ant colony built for it).

It does not cover every algorithm and problem. For those, the top-level [README](../README.md) has an overview and the
Javadoc of each class has the details.

## Reading order

| # | Document | What you learn | Time |
|---|---|---|---|
| 1 | [Architecture](01-architecture.md) | Packages, core interfaces, how a run works, the design rules that every class follows | 1–2 h |
| 2 | [Tools](02-tools.md) | Building, the Metaheuristic Lab, the Problem Lab, the Experiment Runner window and CLI, IDE setup | 1 h + hands-on |
| 3 | [Experiments](03-experiments.md) | The three ways to run experiments, the experiment runner's spec, workspace, journals and statistics | 1–2 h |
| 4 | [The PECTO problem](04-pecto-problem.md) | The model, the encoding, the instances, the operators, efficient evaluation | 1–2 h |
| 5 | [PECTOACO](05-pecto-aco.md) | The ant colony step by step, its parameters, the explorer ants, tuning results, how to change it safely | 2–3 h |

Read them in this order. Each one assumes the ones before it.

## Other documentation in the repository

- [README.md](../README.md): overview of all algorithms and problems, with examples.
- [Problem Lab developer guide](../src/main/java/lab/problemlab/doc/Problem-Lab-Developer-Guide.md): how to add a problem
  to the Problem Lab, with an interactive editor. Read it only if you need to do that.
- [DQN on WFLOP](../src/main/java/metaheuristic/rl/dqn/doc/DQN-on-WFLOP.md): the reinforcement-learning work on the wind
  farm problem. Not needed for PECTO.
- [experiments/INDEX.md](../experiments/INDEX.md) and each experiment's `JOURNAL.md`: what has been run, why, and what
  was concluded. Read the two PECTO journals after the PECTOACO document.
- `pecto ressearch/PECTOACO Diversity Sensitivity.pdf` (one level above the repository): the first study of diversity
  in PECTOACO. **Its parameter names are from an older version of the code** (`explorerAnts`, `partialExplorerRate`,
  restarts). That API no longer exists; its findings were turned into the current explorer parameters (see
  [PECTOACO, section 5.7](05-pecto-aco.md#57-explorer-ants)).

## Getting started in one sitting

You need a JDK 21 or newer (the project's machine uses JDK 25) and an IDE (IntelliJ IDEA is set up: the repository
contains run configurations for both labs in `.run/`).

```bash
cd Metaheuristics
./mvnw test                                                             # Windows: mvnw.cmd test
./mvnw compile exec:java                                                # Metaheuristic Lab
./mvnw compile exec:java -Dexec.mainClass=lab.problemlab.ProblemLab -Dexec.args=PECTO   # Problem Lab on PECTO
```

Then:

1. In the **Problem Lab** (PECTO), generate a small custom instance (10 ambulances, 100 patients, 5 hospitals), make a
   greedy solution and a random one, and compare their costs. Apply `MoveTrip` and `ChangeHospital` a few times and
   watch which routes change and why the cost moves.
2. In the **Metaheuristic Lab**, choose *Casualty transport (PECTO)*, instance *Table I: 1*, add *ACO (MMAS)* and
   *SA*, set *Max evaluations* to 20 000 and run. Look at the *Convergence* and *Metrics* tabs (for ACO:
   `branchingFactor`, `diversity`), then open *View solution*.
3. Read [experiments/pecto-explorer-tuning-r2/JOURNAL.md](../experiments/pecto-explorer-tuning-r2/JOURNAL.md) from top
   to bottom. It is a complete example of how a study is planned, run and concluded here.

## Suggested first week

| Day | Goal |
|---|---|
| 1 | Build, run the tests, read [Architecture](01-architecture.md). Step through one `LocalSearch` run in the debugger, from `Runner.run` to `SearchRun.evaluate`. |
| 2 | Read [Tools](02-tools.md) and use both labs on PECTO, as above. |
| 3 | Read [PECTO](04-pecto-problem.md). Write a small JUnit test that builds a 2-ambulance, 3-patient, 1-hospital instance by hand with `PECTOProblem.builder()` and checks the cost of a solution you compute on paper. |
| 4 | Read [PECTOACO](05-pecto-aco.md) alongside the code (`problems/pecto/aco`). Run `PECTOACOTest` and `PECTOACOGoldenTest`. |
| 5 | Read [Experiments](03-experiments.md). Write a small spec (a custom instance, 2 configurations, 3 repeats, 5 000 evaluations), validate it, submit it, run it with the worker, and write the Conclusions section of its journal. |

## Glossary

| Term | Meaning |
|---|---|
| Representation | The type of a solution (`IntegerPermutation`, `PECTOSolution`, ...). Immutable once handed to an algorithm. |
| Evaluation | One call of `SearchContext.evaluate`, i.e. one objective function computation. Budgets count evaluations. |
| Iteration | One `iterationCompleted` call by the algorithm: for PECTOACO, one colony iteration (all ants + local search + pheromone update). |
| Run | One execution of an algorithm on a problem instance with one seed. Fully reproducible from (algorithm description, instance, seed, budget). |
| Catalog | `lab.model.Catalog`: the list of problems and algorithms, with named parameters, that both the GUI and the experiment runner use. |
| Spec | The JSON file that describes an experiment for the experiment runner. |
| Workspace | The `experiments/` directory: queue, worker state, one folder per experiment. |
| Worker | The background process that runs the experiments of the workspace. |
| Runtime | A frozen copy of the compiled classes in `experiments/runtime/` that the worker runs from. |
| Journal | `JOURNAL.md` of an experiment: question, design, analysis plan, decision rule, results, conclusions. |
| Gap | `(cost − baseline cost) / baseline cost` in %, for the run of the baseline with the same instance and repeat. Negative is better. |
| Table I | The ten PECTO benchmark instances of the paper (200 ambulances, 64 hospitals, 2 430–5 163 patients). `T8` means Table I instance 8. |
