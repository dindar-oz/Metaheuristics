# 2. Tools

The project has three desktop tools and a command line, all in the same code base:

| Tool | Main class | Use it to |
|---|---|---|
| **Metaheuristic Lab** | `lab.MetaheuristicLab` | Run several algorithms side by side on one instance and watch them converge. Quick, interactive comparisons and parameter exploration. |
| **Problem Lab** | `lab.problemlab.ProblemLab` | Understand a problem: build and edit instances and solutions by hand and watch the cost and constraints react. |
| **Experiment Runner** (window + CLI + tray icon) | `experimentrunner.Cli`, `experimentrunner.gui.ExperimentRunnerApp` | Run long, planned experiments in a background process that survives closing the window, the IDE or a reboot. |

Rule of thumb: explore in the labs and **measure with the experiment runner**. Results that go into a paper or a
decision come from the experiment runner, never from the lab.

## 2.1 Building and running

Requirements: a JDK 21 or newer. The Maven wrapper downloads Maven and the dependencies (commons-math3 for the
Mersenne Twister, XChart for charts, FlatLaf for the look and feel, Gson for the runner's JSON, JUnit 5).

```bash
cd Metaheuristics
./mvnw test                                   # build and run the tests (Windows: mvnw.cmd)
./mvnw compile exec:java                      # Metaheuristic Lab (the default main class)
./mvnw compile exec:java -Dexec.mainClass=lab.problemlab.ProblemLab
./mvnw compile exec:java -Dexec.mainClass=lab.problemlab.ProblemLab -Dexec.args=PECTO
./mvnw compile exec:java -Dexec.mainClass=examples.TSPExample          # any example
./mvnw clean compile                          # after IDE build problems (see Architecture 1.10)
```

### IDE

- **IntelliJ IDEA** (recommended): open `Metaheuristics/` as a Maven project. The shared run configurations
  *Metaheuristic Lab* and *Problem Lab* are in `.run/`. Add an *Application* configuration for
  `experimentrunner.Cli` with the working directory set to `Metaheuristics/` and program arguments such as `help`,
  `list` or `gui`.
- **VS Code**: works, but its Java extension can overwrite `target/classes` with broken classes. If you see
  `Unresolved compilation problem`, run `./mvnw clean compile`.

## 2.2 Metaheuristic Lab

```bash
./mvnw compile exec:java
```

Layout:

```
┌ toolbar: ▶ Run  ■ Stop | Max evaluations | Time limit | Target cost | Seed | Problem Lab | Export CSV ┐
├──────────── side panel ─────────────┬───────────────── main area ──────────────────────────────────┤
│ Problem: [Casualty transport (PECTO)]│  Convergence | Metrics   (live XChart plots)                 │
│  parameter table (instance, seed...) │                                                               │
│ Algorithm runs:                      │                                                               │
│  [ACO (MMAS) ▼] [Add]                │                                                               │
│  list of runs  [Duplicate] [Remove]  ├───────────────────────────────────────────────────────────────┤
│  Name + parameter table of the       │  results table (one row per run, live status)  [View solution]│
│  selected run                        │                                                               │
└──────────────────────────────────────┴───────────────────────────────────────────────────────────────┘
```

How to use it:

1. **Choose a problem** and edit its parameters. For PECTO: `instance` (*Table I: 1..10* or *custom*), the custom
   sizes, and `instance seed`.
2. **Add algorithm runs.** Choose an algorithm and click *Add*. Every run has its own name and parameter table, so
   you can add the same algorithm several times with different settings (*Duplicate* copies the selected run). For
   PECTO the algorithms are `EA`, `SA`, `Local Search`, `ACO (MMAS)` (= PECTOACO) and `ACO` (generic ant system on the
   giant-tour encoding).
3. **Set the budget.** *Max evaluations*, *Time limit* (0 = none) and *Target cost*. The run stops at the first limit
   reached. *Seed* is the base seed: run *i* of the list uses `Seeds.derive(seed, i)`, so pressing *Run* again with
   the same settings gives identical results.
4. **Run.** All runs start in parallel on a thread pool. They push their improvements to lock-free queues, and a
   Swing timer drains them, so the GUI never slows the search down.
5. **Watch.**
   - *Convergence*: best cost against evaluations or time (optionally log scale), one curve per run.
   - *Metrics*: any metric the algorithms report (`currentCost`, `meanCost`, SA's `temperature`, PECTOACO's
     `branchingFactor` and `explorerWins`) plus the diagnostics `stagnation`, `diversity`, `duplicates`.
   - The results table: status, best cost, evaluations, time, seed, parameters.
6. **View solution** (or double-click a row): the best solution of the run, final or best so far. With *Live
   update* the window follows the run. For PECTO, `PECTOVisualizer` draws the area: ambulances (squares), patients
   colored by urgency, hospitals (red crosses) and every route in its own color. The table lists the route costs.
   Selecting a route highlights it with its direction, trip numbers and dashed pickup legs. Violations of Eqs. 12–13
   are reported and marked.
7. **Export CSV** saves all convergence curves (run, name, algorithm, seed, evaluations, seconds, best cost,
   parameters).

The lab's problems, algorithms and parameter tables are generated from `lab.model.Catalog` (see
[Architecture 1.7](01-architecture.md#17-the-catalog-one-description-for-the-gui-and-the-experiment-runner)).

**Practical notes for PECTO:**

- On Table I instances an ant takes about 20 ms, so one PECTOACO iteration (20 ants + 100 local search steps + optimal
  hospitals = about 121 evaluations) takes about half a second. 600 iterations is 72 601 evaluations, 1 500 iterations
  is 181 501. A 1 500-iteration run on T8–T10 took 17–31 minutes (mean 20–25) on one core each, with 7 runs in
  parallel on the lab machine.
- The generic `ACO` on the giant tour keeps node × node matrices: about 170 MB for Table I: 1 and 690 MB for
  Table I: 10. Use it on custom instances of a few hundred patients.
- `SA` for PECTO uses the paper's tuned initial temperature (25), cooling 0.9985 to 0.01 in about 100 000
  evaluations.

## 2.3 Problem Lab

```bash
./mvnw compile exec:java -Dexec.mainClass=lab.problemlab.ProblemLab -Dexec.args=PECTO
```

It also opens from the *Problem Lab* button of the Metaheuristic Lab. Use it to build intuition about the problem:
what a good route looks like, what an operator does, why a solution is infeasible.

Main elements:

| Panel | What it does |
|---|---|
| **Instance** | Generate one with a generator (PECTO: *Random (uniform)*, *Table I (paper)*, *Blank area*), with editable parameters, or open a `.pecto` file. Then edit it on the map. |
| **Solution** | Generate one (*Greedy (nearest)*, *Random feasible*, *Empty (no trips)*) or open a `.pectosol` file. Then edit it on the map. |
| **Mutation** | Apply an operator (*MoveTrip*, *ChangeHospital*, *SwapTrips*) once or *n* times. Keep every neighbor (random walk) or only non-worsening / improving ones (hill climber). **Play** applies it repeatedly so you can watch the solution evolve. |
| **Evaluation strip** | The cost, the change caused by the last action, feasibility with violated constraints in words (*3 patient(s) not transported (Eq. 12)*), and measures: trips, ambulances used, longest route, beds used, cost per trip. |
| **History** | Every action with a cost plot. **Undo/Redo** (Ctrl+Z / Ctrl+Y), or click any earlier state. All randomness comes from one seeded RNG, so a sequence of actions can be replayed. |

The PECTO map editor:

- **Tools:** *select / move* (drag a participant), *edit routes* (drag a patient onto a hospital to deliver it there,
  onto an ambulance to append it to that route, onto another patient to insert it before that trip), *add ambulance /
  patient / hospital*.
- **Keyboard:** arrows move the selection; 1–5 set a patient's urgency; +/− change a hospital's beds; U takes a trip
  out, [ and ] move it within its route; Delete removes; **G assigns the untransported patients greedily**. *Help*
  lists them all.
- **Side panel:** properties of the selection; the routes (with each route's cost change); the trips of the
  highlighted route (change a hospital in place, reorder trips); hospitals; patients nobody transports.
- Routes changed by the last action are drawn, with their previous version dashed.
- **Files:** instances are plain text (`*.pecto`), solutions the paper's encoding one route per line (`*.pectosol`).
  See [PECTO, section 4.7](04-pecto-problem.md#47-file-formats).

Exercises worth doing:

1. *Blank area*: build 2 ambulances, 4 patients and 2 hospitals by hand. Assign routes by dragging, compute the cost
   on paper, and compare.
2. Make a greedy solution on a *Random (uniform)* instance, then run the hill climber with *ChangeHospital* only.
   Observe that it stops improving quickly. Then try *MoveTrip*.
3. Set every hospital to few beds and watch the greedy solution send patients to far hospitals.

To support another problem in the Problem Lab, read the
[Problem Lab developer guide](../src/main/java/lab/problemlab/doc/Problem-Lab-Developer-Guide.md).

## 2.4 Experiment Runner window

```
runner.cmd gui            # in Metaheuristics/experiments (Windows); runner.sh gui elsewhere
```

or the desktop shortcut created by `runner.cmd shortcut`, or a double-click on the worker's tray icon.

```
┌ Worker: running, pid 1234 … [Start worker] [Stop after runs] [Stop now]   Threads [7]  [Pause all] ┐
├─ queue: # | Experiment | Title | State | Progress | Runs | Running | Left ────────────────────────┤
│  [Submit spec…] [Pause] [Pause now] [Resume] [Cancel] [Up] [Down] [Summarize] [Open folder]       │
├─ tabs for the selected experiment:  Journal | Summary | Runs in flight ───────────────────────────┤
└───────────────────────────────────────────────────────────────────────────────────────────────────┘
```

- The window **only reads and changes files** in the workspace. The experiments run in the separate worker process,
  so closing the window stops nothing.
- Only one window per workspace: opening it again brings the open one to the front (`gui.lock` + a loopback port).
- *Pause* takes no new runs of the experiment. *Pause now* also stops its runs in flight; they run again later from
  the start. *Cancel* stops it for good and keeps the finished runs.
- *Threads* is the number of runs in parallel. The default is the number of cores minus one.
- *Summarize* rewrites `summary.md` and the journal's results block from the runs finished so far. You can do this
  while an experiment is running.
- *Runs in flight* shows each running run's iterations, evaluations and best cost.

**Tray icon.** While the worker runs, it shows an icon in the system tray. The tooltip shows progress,
double-clicking opens the window, and the menu pauses/resumes everything or stops the worker.

## 2.5 Experiment Runner CLI and window

All commands run in the workspace directory `Metaheuristics/experiments/`:

```
runner.cmd help
runner.cmd validate my-spec.json          # check a spec against the catalog, print the number of runs
runner.cmd submit my-spec.json            # add it to the queue (creates <id>/spec.json and <id>/JOURNAL.md)
runner.cmd start-worker [--heap 12g]      # start the background worker (detached; survives this terminal)
runner.cmd list                           # the queue with progress and time left
runner.cmd status [ID]                    # the worker, or one experiment with its runs in flight
runner.cmd pause ID [--now] | resume ID | cancel ID
runner.cmd move ID up|down|top|bottom
runner.cmd pause-all | resume-all
runner.cmd threads N
runner.cmd stop-worker [--now]            # after the runs in flight, or at once (they will rerun)
runner.cmd summary ID                     # recompute summary.md / journal results now
runner.cmd gui                            # open the window
runner.cmd shortcut                       # desktop shortcut "Experiment Runner" (Windows)
runner.cmd cleanup [--keep N] [--dry-run] # delete old frozen runtimes in runtime/
```

The workspace is `--workspace DIR`, else `$EXPERIMENTS_DIR`, else `./experiments`.

### Where `runner.cmd` comes from

`runner.cmd`, `runner.sh` and `open-runner.cmd` are **generated** and are not in git. They contain the absolute
classpath of the machine (your `target/classes` and your local Maven repository). They are (re)written every time
`experimentrunner.Cli` starts from the build output. On a fresh clone:

1. `./mvnw compile` (so that `target/classes` and the dependencies exist).
2. Run `experimentrunner.Cli` once with the argument `help`, **from the IDE** (working directory `Metaheuristics/`),
   or with plain `java -cp "target/classes;<dependency jars>" experimentrunner.Cli help`.
   `mvnw dependency:build-classpath` prints the dependency jars.
3. From then on use `experiments/runner.cmd` (Windows) or `experiments/runner.sh`.

Do **not** start the CLI with `mvnw exec:java`. It runs inside Maven's own JVM, so the classpath it would write
into the scripts, and freeze for the worker, is Maven's and not the project's.

The full workflow is in [Experiments](03-experiments.md).

## 2.6 Examples and benchmarks

| Class | What it shows |
|---|---|
| `examples.TSPExample` | EA vs SA vs local search vs ACO on a TSP |
| `examples.MonitoringExample` | Listeners, CSV traces, polling a `RunHandle`, cancelling |
| `examples.ContinuousExample`, `SudokuExample`, `WFLOPExample`, `examples.rl.*` | Other problems |
| `problems.pecto.aco.PECTOACOBenchmark` (test sources) | Wall time of PECTOACO on a Table I instance: `java -cp <classes> problems.pecto.aco.PECTOACOBenchmark [instance] [iterations] [runs]` |

There is no PECTO example class. The shortest way to run PECTOACO from code is:

```java
PECTOProblem problem = PECTOInstances.paper(1, 42);          // Table I instance 1, instance seed 42
RunResult<PECTOSolution> r = Runner.of(PECTOACO.builder().build())
        .seed(1).stopWhen(new EvaluationBasedTC(12_101))      // greedy + 100 iterations x 121
        .listener(Listeners.throttled(Listeners.console(), 2000))
        .run(problem);
System.out.println(r + " feasible=" + problem.isFeasible(r.bestSolution()));
```
