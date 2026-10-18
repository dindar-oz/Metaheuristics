package lab.model;

import base.ContinuousProblem;
import lab.view.PECTOVisualizer;
import lab.view.TSPVisualizer;
import lab.view.WFLOPVisualizer;
import base.InitialSolutionGenerator;
import base.MutationOperator;
import base.OptimizationProblem;
import base.RandomPermutationSG;
import metaheuristic.aco.ACO;
import metaheuristic.aco.GraphProblem;
import metaheuristic.de.*;
import metaheuristic.ea.EA;
import metaheuristic.ea.SimpleMutationStrategy;
import metaheuristic.ea.base.CrossOverOperator;
import metaheuristic.ea.base.ReplacementStrategy;
import metaheuristic.ea.crossover.SimpleCrossOverStrategy;
import metaheuristic.ea.crossover.SimplePermutationCrossover;
import metaheuristic.ea.hook.ImmigrationHook;
import metaheuristic.ea.parentselector.TournamentParentSelector;
import metaheuristic.ea.replacement.MuPlusLambdaReplacement;
import metaheuristic.ea.replacement.SteadyStateReplacement;
import metaheuristic.ea.victimselector.SimpleVictimSelector;
import metaheuristic.ls.LocalSearch;
import metaheuristic.pso.PSO;
import metaheuristic.rl.FeaturedConstructiveProblem;
import metaheuristic.rl.dqn.DQN;
import metaheuristic.rl.dqn.Exploration;
import metaheuristic.sa.GeometricCooling;
import metaheuristic.sa.SA;
import operators.RandomRemoveReinsert;
import operators.TwoOpt;
import problems.continuous.Rastrigin;
import problems.continuous.Sphere;
import problems.pecto.PECTOGraph;
import problems.pecto.PECTOInstances;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.aco.PECTOACO;
import problems.pecto.operators.ChangeHospital;
import problems.pecto.operators.MoveTrip;
import problems.pecto.operators.RandomPECTOSG;
import problems.pecto.operators.RouteCrossover;
import problems.sudoku.PlacementMutation;
import problems.sudoku.RandomSDKProblemSG;
import problems.sudoku.RemovalMutation;
import problems.sudoku.SDKProblem;
import problems.sudoku.SDKRepresentation;
import problems.tsp.TSPProblem;
import problems.wflop.CellLayout;
import problems.wflop.DiscreteWFLOP;
import problems.wflop.WFLOPCase;
import problems.wflop.WFLOPProblem;
import problems.wflop.WindScenario;
import problems.wflop.rl.ConstructiveWFLOP;
import representation.DoubleVector;
import representation.IntegerPermutation;
import representation.base.Representation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static lab.model.ParamSpec.*;

/**
 * Everything the lab can run. To offer a new problem or algorithm, add an entry here; the UI is generated from it.
 */
public final class Catalog {

    private Catalog() {
    }

    public static List<LabProblem> problems() {
        return Arrays.asList(tsp(), sudoku(), wflop(), pecto(), continuous("Rastrigin", Rastrigin::new), continuous("Sphere", Sphere::new));
    }

    // ---------------------------------------------------------------- problems

    private static ProblemSpec<IntegerPermutation, TSPProblem> tsp() {
        Function<TSPProblem, InitialSolutionGenerator<IntegerPermutation>> isg = p -> new RandomPermutationSG(p.getCityCount());
        Function<TSPProblem, List<MutationOperator<IntegerPermutation>>> ops =
                p -> Arrays.asList(new TwoOpt(), new RandomRemoveReinsert<>());
        Function<TSPProblem, List<CrossOverOperator<IntegerPermutation>>> crossovers =
                p -> Collections.singletonList(new SimplePermutationCrossover<>());

        return new ProblemSpec<>("TSP (random Euclidean)",
                Arrays.asList(
                        integer("cities", 50, 5, 5000, "number of cities in the unit square"),
                        integer("instance seed", 42, 0, Integer.MAX_VALUE, "seed of the random city coordinates")),
                v -> TSPProblem.randomEuclidean(v.getInt("cities"), v.getInt("instance seed")),
                Arrays.asList(
                        ea(isg, ops, crossovers),
                        sa(isg, ops, 1.0, 1e-6, 0.995, 20),
                        localSearch(isg, ops),
                        aco()),
                new TSPVisualizer());
    }

    private static ProblemSpec<SDKRepresentation, SDKProblem> sudoku() {
        Function<SDKProblem, InitialSolutionGenerator<SDKRepresentation>> isg = RandomSDKProblemSG::new;
        Function<SDKProblem, List<MutationOperator<SDKRepresentation>>> ops =
                p -> Arrays.asList(new PlacementMutation(p), new RemovalMutation(p));

        return new ProblemSpec<>("Sudoku 9x9 (cost 0 = solved)",
                Collections.<ParamSpec>emptyList(),
                v -> new SDKProblem(3, sampleBoard()),
                Arrays.asList(ea(isg, ops, p -> Collections.<CrossOverOperator<SDKRepresentation>>emptyList()),
                        sa(isg, ops, 100, 1e-6, 0.999, 5),
                        localSearch(isg, ops)));
    }

    private static ProblemSpec<DoubleVector, ContinuousProblem> continuous(String name, Function<Integer, ContinuousProblem> factory) {
        return new ProblemSpec<>(name,
                Collections.singletonList(integer("dimension", 20, 1, 10000, "number of variables")),
                v -> factory.apply(v.getInt("dimension")),
                continuousAlgorithms());
    }

    // Yu & Zhang (2024): 10 x 10 grid, Jensen wake model, objective = cost / power.
    // DE and PSO use the paper's real-vector encodings; EA, SA and local search work on cell sets directly,
    // and DQN builds cell sets turbine by turbine.
    private static LabProblem wflop() {
        List<ParamSpec> params = Arrays.asList(
                choice("case", "Case 1", "1-2: 1-100 turbines, 3-4: fixed count; 1,3: full wake, 2,4: partial wake",
                        "Case 1", "Case 2", "Case 3", "Case 4"),
                choice("wind", "Condition 1", "1: 12 m/s from 36 directions; 2: 8/12/17 m/s by direction sector",
                        "Condition 1", "Condition 2"),
                integer("turbines (cases 3-4)", 39, 1, 100, "number of turbines for Cases 3 and 4"));

        ProblemSpec<DoubleVector, WFLOPProblem> continuous = new ProblemSpec<DoubleVector, WFLOPProblem>("continuous encoding", params,
                v -> wflopCase(v).create(wind(v), v.getInt("turbines (cases 3-4)")),
                continuousAlgorithms(),
                WFLOPVisualizer.continuous());

        ProblemSpec<CellLayout, DiscreteWFLOP> discrete = new ProblemSpec<CellLayout, DiscreteWFLOP>("discrete", params,
                v -> wflopCase(v).createDiscrete(wind(v), v.getInt("turbines (cases 3-4)")),
                Arrays.asList(
                        ea(DiscreteWFLOP::randomLayouts, DiscreteWFLOP::neighborhood, p -> Collections.singletonList(p.crossover())),
                        // objective differences between neighbors are ~1e-6, so SA needs a matching temperature scale
                        sa(DiscreteWFLOP::randomLayouts, DiscreteWFLOP::neighborhood, 1e-5, 1e-9, 0.998, 10),
                        localSearch(DiscreteWFLOP::randomLayouts, DiscreteWFLOP::neighborhood),
                        dqn(ConstructiveWFLOP::new)),
                WFLOPVisualizer.discrete());

        return new MultiEncodingProblem("Wind farm layout (WFLOP)", params, Arrays.asList(continuous, discrete));
    }

    // Balci & Oz: ambulances take patients, one at a time, to hospitals of limited capacity; the paper's Table I
    // instances or a custom size generated the same way. EA, SA, local search and the MAX-MIN ant colony (PECTOACO)
    // work on routes; ACO builds giant tours over the ambulances and patients (PECTOGraph), which decode to routes.
    private static LabProblem pecto() {
        String[] instances = new String[PECTOInstances.INSTANCE_COUNT + 1];
        for (int k = 1; k <= PECTOInstances.INSTANCE_COUNT; k++)
            instances[k - 1] = "Table I: " + k;
        instances[PECTOInstances.INSTANCE_COUNT] = "custom";
        List<ParamSpec> params = Arrays.asList(
                choice("instance", instances[0], "Table I: 200 ambulances, 64 hospitals of 81 beds, 2430-5163 patients; "
                        + "custom: the sizes below", instances),
                integer("ambulances (custom)", 10, 1, 100000, "number of ambulances of a custom instance"),
                integer("patients (custom)", 100, 0, 1000000, "number of patients of a custom instance"),
                integer("hospitals (custom)", 5, 1, 100000, "number of hospitals of a custom instance"),
                integer("beds per hospital (custom)", 30, 0, 1000000, "capacity of every hospital of a custom instance"),
                integer("instance seed", 42, 0, Integer.MAX_VALUE, "seed of the coordinates and urgencies"));

        Function<PECTOProblem, InitialSolutionGenerator<PECTOSolution>> isg = RandomPECTOSG::new;
        Function<PECTOProblem, List<MutationOperator<PECTOSolution>>> ops = p -> Arrays.asList(new MoveTrip(), new ChangeHospital());
        Function<PECTOProblem, List<CrossOverOperator<PECTOSolution>>> crossovers = p -> Collections.singletonList(new RouteCrossover());
        ProblemSpec<PECTOSolution, PECTOProblem> routes = new ProblemSpec<>("routes", params, Catalog::pectoInstance,
                Arrays.asList(
                        ea(isg, ops, crossovers),
                        // the paper's tuned initial temperature; cooled to the minimum in ~100 000 evaluations
                        sa(isg, ops, 25, 0.01, 0.9985, 20),
                        localSearch(isg, ops),
                        pectoAco()),
                new PECTOVisualizer());

        // ACO keeps three node x node matrices: ~170 MB for Table I: 1, ~690 MB for Table I: 10
        PECTOVisualizer visualizer = new PECTOVisualizer();
        ProblemSpec<IntegerPermutation, PECTOGraph> tours = new ProblemSpec<IntegerPermutation, PECTOGraph>("giant tour", params,
                v -> new PECTOGraph(pectoInstance(v)),
                Collections.singletonList(aco()),
                (graph, tour) -> visualizer.view(graph.problem(), graph.decode(tour)));

        return new MultiEncodingProblem("Casualty transport (PECTO)", params, Arrays.asList(routes, tours));
    }

    private static PECTOProblem pectoInstance(ParamValues v) {
        String instance = v.getChoice("instance");
        long seed = v.getInt("instance seed");
        if (instance.equals("custom"))
            return PECTOInstances.random(v.getInt("ambulances (custom)"), v.getInt("patients (custom)"),
                    v.getInt("hospitals (custom)"), v.getInt("beds per hospital (custom)"), seed);
        return PECTOInstances.paper(Integer.parseInt(instance.substring("Table I: ".length())), seed);
    }

    private static WFLOPCase wflopCase(ParamValues v) {
        return WFLOPCase.values()[v.getChoice("case").charAt(5) - '1'];
    }

    private static WindScenario wind(ParamValues v) {
        return v.getChoice("wind").equals("Condition 2") ? WindScenario.condition2() : WindScenario.condition1();
    }

    // DE and PSO for any continuous problem type P
    private static <P extends ContinuousProblem> List<AlgorithmSpec<DoubleVector, P>> continuousAlgorithms() {
        return Arrays.asList(
                new AlgorithmSpec<DoubleVector, P>("DE",
                        Arrays.asList(
                                choice("boundary", "Saturation", "how out-of-bounds mutants are repaired",
                                        "Saturation", "Mirroring", "Midpoint", "Random"),
                                integer("population size", 50, 4, 100000, "number of individuals"),
                                real("CR", 0.9, 0, 1, "crossover rate"),
                                real("F", 0.5, 0, 2, "differential weight")),
                        (p, v) -> DifferentialEvolution.builder()
                                .boundaryHandler(boundaryHandler(v.getChoice("boundary")))
                                .populationSize(v.getInt("population size"))
                                .crossoverRate(v.getDouble("CR"))
                                .differentialWeight(v.getDouble("F"))
                                .build()),
                new AlgorithmSpec<DoubleVector, P>("PSO",
                        Arrays.asList(
                                real("inertia", 0.7298, 0, 2, "w"),
                                real("cognitive", 1.49618, 0, 5, "attraction to the particle's best (c1)"),
                                real("social", 1.49618, 0, 5, "attraction to the swarm's best (c2)"),
                                integer("swarm size", 40, 1, 100000, "number of particles")),
                        (p, v) -> PSO.builder()
                                .inertia(v.getDouble("inertia"))
                                .cognitive(v.getDouble("cognitive"))
                                .social(v.getDouble("social"))
                                .swarmSize(v.getInt("swarm size"))
                                .build()));
    }

    // ---------------------------------------------------------------- generic algorithms

    private static <S extends Representation<S>, P extends OptimizationProblem<S>> AlgorithmSpec<S, P> ea(
            Function<P, InitialSolutionGenerator<S>> isg, Function<P, List<MutationOperator<S>>> ops,
            Function<P, List<CrossOverOperator<S>>> crossovers) {
        return new AlgorithmSpec<>("EA",
                Arrays.asList(
                        integer("population size", 50, 2, 100000, "number of individuals"),
                        real("crossover rate", 0.8, 0, 1, "probability of crossover per generation"),
                        real("mutation rate", 0.8, 0, 1, "probability of mutation per generation"),
                        integer("tournament size", 3, 1, 1000, "parent selection pressure"),
                        choice("replacement", "steady state", "how offsprings enter the population", "steady state", "mu+lambda"),
                        integer("immigration period", 0, 0, 1000000, "generations between immigrations (0 = off)")),
                (p, v) -> {
                    ReplacementStrategy replacement = v.getChoice("replacement").equals("mu+lambda")
                            ? new MuPlusLambdaReplacement() : new SteadyStateReplacement(new SimpleVictimSelector());
                    EA.Builder<S> b = EA.builder(isg.apply(p))
                            .crossovers(crossovers.apply(p))
                            .mutations(ops.apply(p))
                            .populationSize(v.getInt("population size"))
                            .crossOverStrategy(new SimpleCrossOverStrategy(v.getDouble("crossover rate")))
                            .mutationStrategy(new SimpleMutationStrategy(v.getDouble("mutation rate"), 1))
                            .parentSelector(new TournamentParentSelector(v.getInt("tournament size"), 2))
                            .replacementStrategy(replacement);
                    int period = v.getInt("immigration period");
                    if (period > 0)
                        b.hook(new ImmigrationHook<>(period, Math.max(1, v.getInt("population size") / 5)));
                    return b.build();
                });
    }

    private static <S extends Representation<S>, P extends OptimizationProblem<S>> AlgorithmSpec<S, P> sa(
            Function<P, InitialSolutionGenerator<S>> isg, Function<P, List<MutationOperator<S>>> ops,
            double t0, double tMin, double cooling, int innerLoop) {
        return new AlgorithmSpec<>("SA",
                Arrays.asList(
                        real("initial temperature", t0, 1e-12, 1e12, "starting temperature"),
                        real("minimum temperature", tMin, 0, 1e12, "the run finishes below this temperature"),
                        real("cooling rate", cooling, 1e-6, 0.999999999, "geometric cooling factor"),
                        integer("inner loop", innerLoop, 1, 1000000, "neighbors per temperature level")),
                (p, v) -> SA.builder(ops.apply(p), isg.apply(p))
                        .initialTemperature(v.getDouble("initial temperature"))
                        .minimumTemperature(v.getDouble("minimum temperature"))
                        .cooling(new GeometricCooling(v.getDouble("cooling rate")))
                        .innerLoopLength(v.getInt("inner loop"))
                        .build());
    }

    private static <S extends Representation<S>, P extends OptimizationProblem<S>> AlgorithmSpec<S, P> localSearch(
            Function<P, InitialSolutionGenerator<S>> isg, Function<P, List<MutationOperator<S>>> ops) {
        return new AlgorithmSpec<>("Local Search",
                Collections.singletonList(integer("max non-improving", 5000, 1, Integer.MAX_VALUE,
                        "consecutive failed neighbors after which the search ends")),
                (p, v) -> LocalSearch.builder(ops.apply(p), isg.apply(p)).maxNonImproving(v.getInt("max non-improving")).build());
    }

    // Ant System for any graph problem type P
    private static <P extends GraphProblem> AlgorithmSpec<IntegerPermutation, P> aco() {
        return new AlgorithmSpec<>("ACO",
                Arrays.asList(
                        integer("ants", 20, 1, 10000, "ants per iteration"),
                        real("alpha", 1, 0, 20, "pheromone influence"),
                        real("beta", 2, 0, 20, "distance influence"),
                        real("rho", 0.5, 0, 1, "evaporation rate")),
                (p, v) -> ACO.builder()
                        .antCount(v.getInt("ants"))
                        .alpha(v.getDouble("alpha"))
                        .beta(v.getDouble("beta"))
                        .rho(v.getDouble("rho"))
                        .build());
    }

    // MAX-MIN ant colony that builds PECTO routes directly
    private static AlgorithmSpec<PECTOSolution, PECTOProblem> pectoAco() {
        return new AlgorithmSpec<>("ACO (MMAS)",
                Arrays.asList(
                        integer("ants", 20, 1, 10000, "ants per iteration"),
                        real("alpha", 1, 0, 20, "pheromone influence"),
                        real("beta", 2, 0, 20, "heuristic influence"),
                        real("rho", 0.1, 0.001, 1, "evaporation rate"),
                        real("p best", 0.05, 0.001, 0.999, "sets tau_min: the probability of building the best solution "
                                + "once the pheromone has converged"),
                        integer("candidates", 20, 1, 1000, "nearest patients considered after each stop"),
                        integer("hospital candidates", 5, 1, 1000, "nearest hospitals with a free bed considered for a patient"),
                        integer("shortlist", 3, 1, 100000, "best rated ambulances among which the moving one is drawn"),
                        integer("local search steps", 100, 0, 1000000, "neighbors tried on the best ant of every iteration"),
                        choice("hospitals", "optimal", "optimal: the best ant of every iteration gets the best hospitals "
                                + "for its routes; as built: it keeps the ones the ant chose", "optimal", "as built"),
                        choice("execution", "sequential", "synchronous: the ants of every iteration are built in "
                                + "parallel; both give the same run for the same seed", "sequential", "synchronous"),
                        integer("threads", Runtime.getRuntime().availableProcessors(), 1, 1024,
                                "threads building the ants in synchronous execution"),
                        real("tau min ratio", 0, 0, 0.999, "floor of tau_min as a fraction of tau_max, e.g. 0.025; "
                                + "0 = from p best only"),
                        integer("explorers", 2, 0, 10000, "ants per iteration that sometimes ignore the pheromone "
                                + "(at most ants); 0 = the plain colony"),
                        real("explorer rate", 0.05, 0, 1, "probability that an explorer's patient or hospital draw "
                                + "ignores the pheromone"),
                        real("explorer beta", 0.5, 0, 20, "heuristic influence in the draws that ignore the pheromone; "
                                + "lower than beta spreads them over the candidates"),
                        real("explorer deposit", 0.05, 0, 1, "pheromone deposited by the best explorer of every "
                                + "iteration, as a fraction of 1 / cost; 0 = none")),
                (p, v) -> PECTOACO.builder()
                        .antCount(v.getInt("ants"))
                        .alpha(v.getDouble("alpha"))
                        .beta(v.getDouble("beta"))
                        .rho(v.getDouble("rho"))
                        .pBest(v.getDouble("p best"))
                        .candidates(v.getInt("candidates"))
                        .hospitalCandidates(v.getInt("hospital candidates"))
                        .shortlist(v.getInt("shortlist"))
                        .localSearchSteps(v.getInt("local search steps"))
                        .optimalHospitals(v.getChoice("hospitals").equals("optimal"))
                        .executionMode(v.getChoice("execution").equals("synchronous")
                                ? PECTOACO.ExecutionMode.SYNCHRONOUS : PECTOACO.ExecutionMode.SEQUENTIAL)
                        .threads(v.getInt("threads"))
                        .tauMinRatio(v.getDouble("tau min ratio"))
                        .explorers(Math.min(v.getInt("explorers"), v.getInt("ants")))
                        .explorerRate(v.getDouble("explorer rate"))
                        .explorerBeta(v.getDouble("explorer beta"))
                        .explorerDeposit(v.getDouble("explorer deposit"))
                        .build());
    }

    // DQN on the constructive view of problem type P; each episode builds one solution and costs one evaluation
    private static <S extends Representation<S>, P extends OptimizationProblem<S>> AlgorithmSpec<S, P> dqn(
            Function<P, FeaturedConstructiveProblem<S>> view) {
        return new AlgorithmSpec<>("DQN",
                Arrays.asList(
                        choice("exploration", "softmax", "softmax explores among the actions valued nearly as high "
                                + "as the best; epsilon-greedy tries uniformly random actions", "softmax", "epsilon-greedy"),
                        real("exploration start", 0.3, 1e-6, 100, "initial temperature or epsilon (at most 1)"),
                        real("exploration end", 0.05, 1e-6, 100, "final temperature or epsilon (at most 1)"),
                        integer("decay episodes", 1000, 1, Integer.MAX_VALUE, "episodes over which exploration decays"),
                        integer("hidden units", 16, 1, 1024, "units of each of the two hidden layers"),
                        real("learning rate", 1e-3, 1e-7, 1, "Adam step size"),
                        integer("n-step", 3, 1, 1000, "steps of real rewards before bootstrapping"),
                        integer("candidates", 16, 0, 100000, "actions per state the bootstrap targets maximize over (0 = all)"),
                        real("elite fraction", 0.25, 0, 1, "share of every batch replayed from the best episode"),
                        integer("greedy every", 10, 0, 1000000, "every n-th episode is greedy (0 = never)")),
                (p, v) -> {
                    double start = v.getDouble("exploration start"), end = v.getDouble("exploration end");
                    int decay = v.getInt("decay episodes");
                    int hidden = v.getInt("hidden units");
                    return DQN.<S>builder()
                            .exploration(v.getChoice("exploration").equals("softmax")
                                    ? Exploration.softmax(start, end, decay) : Exploration.epsilonGreedy(start, end, decay))
                            .hidden(hidden, hidden)
                            .learningRate(v.getDouble("learning rate"))
                            .nStep(v.getInt("n-step"))
                            .candidates(v.getInt("candidates"))
                            .eliteFraction(v.getDouble("elite fraction"))
                            .greedyEvery(v.getInt("greedy every"))
                            .build()
                            .viewing(view);
                });
    }

    // ---------------------------------------------------------------- helpers

    private static BoundaryHandler boundaryHandler(String name) {
        switch (name) {
            case "Mirroring":
                return new MirroringBH();
            case "Midpoint":
                return new MidpointBH();
            case "Random":
                return new RandomBH();
            default:
                return new SaturationBH();
        }
    }

    private static int[][] sampleBoard() {
        int[][] board = new int[9][9];
        board[0][3] = 3;
        board[4][2] = 6;
        board[0][2] = 1;
        board[8][8] = 8;
        board[5][5] = 5;
        board[5][6] = 4;
        return board;
    }
}
