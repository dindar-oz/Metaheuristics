package examples;

import experiments.ConsoleERH;
import experiments.Experiment;
import experiments.ExperimentCase;
import metaheuristic.de.*;
import metaheuristic.pso.PSO;
import metaheuristic.termination.EvaluationBasedTC;
import problems.continuous.Rastrigin;

/**
 * Compares DE with different boundary handlers and PSO on the 20-dimensional Rastrigin function.
 */
public class ContinuousExample {

    static DifferentialEvolution de(BoundaryHandler boundaryHandler) {
        return DifferentialEvolution.builder()
                .boundaryHandler(boundaryHandler)
                .populationSize(50)
                .crossoverRate(0.9)
                .differentialWeight(0.5)
                .build();
    }

    public static void main(String[] args) {
        Rastrigin problem = new Rastrigin(20);

        Experiment.of(
                        ExperimentCase.of("DE-Saturation", de(new SaturationBH()), problem),
                        ExperimentCase.of("DE-Mirror", de(new MirroringBH()), problem),
                        ExperimentCase.of("DE-Midpoint", de(new MidpointBH()), problem),
                        ExperimentCase.of("DE-Random", de(new RandomBH()), problem),
                        ExperimentCase.of("PSO", PSO.builder().inertia(0.5034).cognitive(1.0519).social(2.3403).swarmSize(50).build(), problem))
                .repeats(5)
                .seed(42)
                .stopWhen(new EvaluationBasedTC(100000))
                .threads(Runtime.getRuntime().availableProcessors())
                .handler(new ConsoleERH())
                .run();
    }
}
