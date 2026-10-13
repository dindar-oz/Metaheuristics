package examples.rl;

import experiments.Experiment;
import experiments.ExperimentCase;
import metaheuristic.rl.GreedyConstruction;
import metaheuristic.rl.dqn.DQN;
import metaheuristic.rl.nn.Mlp;
import metaheuristic.run.RunResult;
import metaheuristic.run.Runner;
import metaheuristic.sa.GeometricCooling;
import metaheuristic.sa.SA;
import metaheuristic.termination.EvaluationBasedTC;
import problems.wflop.CellLayout;
import problems.wflop.DiscreteWFLOP;
import problems.wflop.WFLOPCase;
import problems.wflop.WindFarmParameters;
import problems.wflop.WindScenario;
import problems.wflop.rl.ConstructiveWFLOP;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Transfers what DQN learns on a small wind farm to larger ones. The features of ConstructiveWFLOP do not depend on
 * the size of the farm, so a network trained on one farm can build layouts on another with the same turbines and
 * cell size.
 * <ol>
 *   <li>DQN is trained on the paper's 10 x 10 farm (39 turbines, full wake, Wind Condition 1) and its network saved;</li>
 *   <li>on 15 x 15 and 20 x 20 farms with the same turbine density (88 and 156 turbines), with the same budget:
 *       the greedy heuristic (one evaluation), SA, DQN from scratch, DQN starting from the saved network and still
 *       learning (fine-tuned), and DQN only using the saved network (frozen).</li>
 * </ol>
 * Arguments: [evaluations per run on the larger farms, default 300] [training episodes on 10 x 10, default 2500]
 * [runs per algorithm, default 3]
 */
public class WFLOPTransferExample {

    static final double DENSITY = 0.39;   // 39 turbines on 100 cells, as in Cases 3 and 4

    public static void main(String[] args) throws IOException {
        int budget = args.length > 0 ? Integer.parseInt(args[0]) : 300;
        int trainingEpisodes = args.length > 1 ? Integer.parseInt(args[1]) : 2500;
        int runs = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        WindScenario wind = WindScenario.condition1();

        // 1. learn on the paper's farm and save the network
        Path file = Paths.get("results", "dqn-wflop-10x10.mlp");
        AtomicReference<Mlp> trained = new AtomicReference<>();
        DQN<CellLayout> learner = DQN.<CellLayout>builder().trainedNetwork(trained::set).build();
        RunResult<CellLayout> source = Runner.of(learner).seed(2024).stopWhen(new EvaluationBasedTC(trainingEpisodes))
                .run(new ConstructiveWFLOP(farm(10, wind)));
        Files.createDirectories(file.getParent());
        trained.get().save(file);
        System.out.printf("Trained on 10 x 10 cells, 39 turbines: best %.8f in %.0f s; network saved to %s%n%n",
                source.bestCost(), source.elapsedMillis() / 1000.0, file);

        // 2. use it on larger farms
        Mlp network = Mlp.load(file);
        for (int side : new int[]{15, 20}) {
            DiscreteWFLOP discrete = farm(side, wind);
            ConstructiveWFLOP constructive = new ConstructiveWFLOP(discrete);
            System.out.printf("%d x %d cells, %d turbines, %d evaluations per run%n", side, side, discrete.maxTurbines(), budget);
            Experiment.of(
                            ExperimentCase.of("Greedy", new GreedyConstruction<CellLayout>(), constructive),
                            ExperimentCase.of("SA", sa(discrete, budget), discrete),
                            ExperimentCase.of("DQN", DQN.<CellLayout>builder()
                                    .softmax(0.3, 0.05, budget / 2).build(), constructive),
                            ExperimentCase.of("DQN, tuned", DQN.<CellLayout>builder().initialNetwork(network)
                                    .softmax(0.1, 0.05, budget / 2).build(), constructive),
                            ExperimentCase.of("DQN, frozen", DQN.<CellLayout>builder().initialNetwork(network).training(false)
                                    .softmax(0.1, 0.1, 1).greedyEvery(budget).build(), constructive))
                    .repeats(runs)
                    .seed(2024)
                    .stopWhen(new EvaluationBasedTC(budget))
                    .threads(Runtime.getRuntime().availableProcessors())
                    .handler(new WFLOPDQNExample.Table(discrete))
                    .run();
            System.out.println();
        }
    }

    // a full-wake farm of side x side cells of 200 m with the turbine density of the paper's fixed-count cases
    public static DiscreteWFLOP farm(int side, WindScenario wind) {
        WindFarmParameters parameters = WindFarmParameters.builder().farmSize(200.0 * side).build();
        return WFLOPCase.CASE_3.createDiscrete(parameters, wind, (int) Math.round(DENSITY * side * side));
    }

    // SA cooling from 1e-5 to 1e-9 within the budget
    public static SA<CellLayout> sa(DiscreteWFLOP discrete, int budget) {
        int innerLoop = 10;
        double cooling = Math.pow(1e-9 / 1e-5, innerLoop / (double) budget);
        return SA.builder(discrete.neighborhood(), discrete.randomLayouts())
                .initialTemperature(1e-5).minimumTemperature(1e-9).cooling(new GeometricCooling(cooling))
                .innerLoopLength(innerLoop).build();
    }
}
