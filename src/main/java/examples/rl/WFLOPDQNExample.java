package examples.rl;

import experiments.Experiment;
import experiments.ExperimentCase;
import experiments.ExperimentResultHandler;
import metaheuristic.ls.LocalSearch;
import metaheuristic.rl.dqn.DQN;
import metaheuristic.run.RunResult;
import metaheuristic.sa.GeometricCooling;
import metaheuristic.sa.SA;
import metaheuristic.termination.EvaluationBasedTC;
import problems.wflop.CellLayout;
import problems.wflop.DiscreteWFLOP;
import problems.wflop.LayoutEvaluation;
import problems.wflop.WFLOPCase;
import problems.wflop.WindScenario;
import problems.wflop.rl.ConstructiveWFLOP;

import java.util.List;

/**
 * DQN as a constructive metaheuristic for wind farm layout optimization, against SA and local search on the discrete
 * layouts, under the paper's protocol (2500 evaluations, 10 runs per case). DQN spends one evaluation per episode;
 * its step rewards come from incremental evaluations of the partial layouts that are not counted, so also compare
 * the run times. Pass "2" as argument for Wind Condition 2.
 */
public class WFLOPDQNExample {

    public static void main(String[] args) {
        WindScenario wind = args.length > 0 && args[0].equals("2") ? WindScenario.condition2() : WindScenario.condition1();
        System.out.println("Wind " + wind + "\n");

        for (WFLOPCase c : WFLOPCase.values()) {
            DiscreteWFLOP discrete = c.createDiscrete(wind);
            ConstructiveWFLOP constructive = new ConstructiveWFLOP(discrete);
            System.out.println(c);
            Experiment.of(
                            ExperimentCase.of("DQN", DQN.<CellLayout>builder().build(), constructive),
                            ExperimentCase.of("SA", SA.builder(discrete.neighborhood(), discrete.randomLayouts())
                                    .initialTemperature(1e-5).minimumTemperature(1e-9).cooling(new GeometricCooling(0.97))
                                    .innerLoopLength(10).build(), discrete),
                            ExperimentCase.of("LS", LocalSearch.builder(discrete.neighborhood(), discrete.randomLayouts())
                                    .maxNonImproving(500).build(), discrete))
                    .repeats(10)
                    .seed(2024)
                    .stopWhen(new EvaluationBasedTC(2500))
                    .threads(Runtime.getRuntime().availableProcessors())
                    .handler(new Table(discrete))
                    .run();
            System.out.println();
        }
    }

    // min/max/mean/std of the objective, mean run time, and the indicators of the best layout
    static final class Table implements ExperimentResultHandler {
        private final DiscreteWFLOP problem;

        Table(DiscreteWFLOP problem) {
            this.problem = problem;
        }

        @Override
        public void begin(long baseSeed) {
            System.out.printf("  %-12s %11s %11s %11s %9s %9s %12s %11s %4s%n",
                    "", "min", "max", "mean", "std", "time (s)", "power (kW)", "efficiency", "N");
        }

        @Override
        public void handle(ExperimentCase<?, ?> experimentCase, List<RunResult<?>> runs) {
            double min = Double.MAX_VALUE, max = 0, sum = 0, millis = 0;
            RunResult<?> best = null;
            for (RunResult<?> r : runs) {
                sum += r.bestCost();
                max = Math.max(max, r.bestCost());
                millis += r.elapsedMillis();
                if (r.bestCost() < min) {
                    min = r.bestCost();
                    best = r;
                }
            }
            double mean = sum / runs.size(), var = 0;
            for (RunResult<?> r : runs)
                var += (r.bestCost() - mean) * (r.bestCost() - mean);
            LayoutEvaluation e = problem.evaluate((CellLayout) best.bestSolution());
            System.out.printf("  %-12s %11.8f %11.8f %11.8f %9.2e %9.2f %12.2f %10.2f%% %4d%n", experimentCase.getTitle(),
                    min, max, mean, Math.sqrt(var / runs.size()), millis / runs.size() / 1000, e.power(),
                    100 * e.efficiency(), e.turbines());
        }

        @Override
        public void end() {
        }
    }
}
