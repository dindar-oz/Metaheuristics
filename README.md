# Metaheuristic Algorithms in Java

A small, modular Java toolkit of metaheuristic algorithms, problem models and an experiment runner.

## Algorithms

| Algorithm | Class | Works on |
|---|---|---|
| Evolutionary / Genetic Algorithm | `metaheuristic.ea.EA` | any representation with suitable operators |
| Simulated Annealing | `metaheuristic.sa.SA` | any representation with mutation operators |
| Local Search (stochastic hill climbing) | `metaheuristic.ls.LocalSearch` | any representation with mutation operators |
| Particle Swarm Optimization | `metaheuristic.pso.PSO` | `ContinuousProblem` |
| Differential Evolution (+ 4 boundary handlers) | `metaheuristic.de.DifferentialEvolution` | `ContinuousProblem` |
| Ant Colony Optimization (Ant System) | `metaheuristic.aco.ACO` | `GraphProblem` (e.g. TSP) |

## Problems

- **TSP**: `problems.tsp.TSPProblem` (random Euclidean instances or your own distance matrix / coordinates)
- **Sudoku**: `problems.sudoku.SDKProblem`

Add your own problem by implementing `base.OptimizationProblem` (minimization), or
`base.ContinuousProblem` / `metaheuristic.aco.GraphProblem` to use PSO/DE or ACO.

## Project layout

```
src/main/java/
base/            OptimizationProblem, ContinuousProblem, MutationOperator, TerminalCondition,
                 InitialSolutionGenerator (+ random permutation / double-vector generators)
base/terminalcondition/  evaluation-, iteration-, time-, cost- and stagnation-based stop criteria (+ and/or)
operators/       generic mutation operators for Array/permutation representations (2-opt, swap, insert, ...)
representation/  IntegerPermutation, IntegerVector, DoubleVector, IntegerMatrix, BinaryString, populations
metaheuristic/   AbstractMetaheuristic (template: init -> run -> complete), listeners, iteration events
experiments/     Experiment runner: repeats cases, reports best/mean/std cost, evaluations, time-to-best
gui/             JFreeChart convergence plot of several algorithms running in parallel
examples/        runnable demos
```

## Running the examples

Requires Java 8+. The project is built with Maven; the bundled wrapper (`mvnw` / `mvnw.cmd`)
downloads Maven and the dependencies (commons-math3, jfreechart) automatically, so nothing needs to be installed.

```bash
./mvnw package                                                      # build target/metaheuristics-1.0-SNAPSHOT.jar
./mvnw compile exec:java                                            # EA vs SA vs LocalSearch vs ACO on TSP
./mvnw compile exec:java -Dexec.mainClass=examples.ContinuousExample   # DE boundary handlers vs PSO on Rastrigin
./mvnw compile exec:java -Dexec.mainClass=examples.SudokuExample       # EA and SA on a sudoku
./mvnw compile exec:java -Dexec.mainClass=examples.TSPGuiExample       # live convergence chart
```

On Windows use `mvnw.cmd` instead of `./mvnw`. IntelliJ IDEA and VS Code import the project directly from `pom.xml`.

Call `RandUtil.setSeed(...)` before a run for reproducible results.

## License

This project is licensed under the [MIT License](LICENSE).
