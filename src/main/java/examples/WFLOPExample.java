package examples;

import experiments.Experiment;
import experiments.ExperimentCase;
import experiments.ExperimentResultHandler;
import metaheuristic.de.DifferentialEvolution;
import metaheuristic.de.SaturationBH;
import metaheuristic.pso.PSO;
import metaheuristic.run.RunResult;
import metaheuristic.termination.EvaluationBasedTC;
import metaheuristic.ea.EA;
import metaheuristic.ls.LocalSearch;
import metaheuristic.sa.GeometricCooling;
import metaheuristic.sa.SA;
import problems.wflop.CellLayout;
import problems.wflop.DiscreteWFLOP;
import problems.wflop.LayoutEvaluation;
import problems.wflop.WFLOPCase;
import problems.wflop.WFLOPProblem;
import problems.wflop.WindScenario;
import representation.DoubleVector;

import java.util.List;

/**
 * Wind farm layout optimization (Yu &amp; Zhang 2024) under the paper's protocol (2500 evaluations, 10 independent runs
 * per case): DE and PSO (population 50) on the paper's real-vector encodings, and EA, SA and local search (marked *)
 * on the discrete layout representation. All use the same objective, so the numbers are directly comparable.
 * Pass "2" as argument for Wind Condition 2.
 */
public class WFLOPExample {

    public static void main(String[] args) {
        WindScenario wind = args.length > 0 && args[0].equals("2") ? WindScenario.condition2() : WindScenario.condition1();
        System.out.println("Wind " + wind + "\n");

        for (WFLOPCase c : WFLOPCase.values()) {
            WFLOPProblem problem = c.create(wind);               // the paper's real-vector encoding
            DiscreteWFLOP discrete = c.createDiscrete(wind);      // the same case over cell sets
            System.out.println(c);
            Experiment.of(
                            ExperimentCase.of("DE", DifferentialEvolution.builder()
                                    .boundaryHandler(new SaturationBH()).populationSize(50).crossoverRate(0.9).differentialWeight(0.5).build(), problem),
                            ExperimentCase.of("PSO", PSO.builder().swarmSize(50).build(), problem),
                            ExperimentCase.of("EA*", EA.builder(discrete.randomLayouts())
                                    .mutations(discrete.neighborhood()).crossovers(discrete.crossover()).populationSize(50).build(), discrete),
                            ExperimentCase.of("SA*", SA.builder(discrete.neighborhood(), discrete.randomLayouts())
                                    .initialTemperature(1e-5).minimumTemperature(1e-9).cooling(new GeometricCooling(0.97))
                                    .innerLoopLength(10).build(), discrete),
                            ExperimentCase.of("LS*", LocalSearch.builder(discrete.neighborhood(), discrete.randomLayouts())
                                    .maxNonImproving(500).build(), discrete))
                    .repeats(10)
                    .seed(2024)
                    .stopWhen(new EvaluationBasedTC(2500))
                    .threads(Runtime.getRuntime().availableProcessors())
                    .handler(new PaperTable(problem))
                    .run();
            System.out.println();
        }
    }

    /**
     * Prints a row like Tables 6-9: min/max/mean/std of the objective and the indicators of the best layout.
     */
    static final class PaperTable implements ExperimentResultHandler {
        private final WFLOPProblem problem;
        private LayoutEvaluation overallBest;

        PaperTable(WFLOPProblem problem) {
            this.problem = problem;
        }

        @Override
        public void begin(long baseSeed) {
            System.out.printf("  %-5s %10s %10s %10s %10s %14s %9s %11s %4s%n",
                    "", "min", "max", "mean", "std", "power (kW)", "cost", "efficiency", "N");
        }

        @Override
        public void handle(ExperimentCase<?, ?> experimentCase, List<RunResult<?>> runs) {
            double min = Double.MAX_VALUE, max = 0, sum = 0;
            RunResult<?> best = null;
            for (RunResult<?> r : runs) {
                sum += r.bestCost();
                max = Math.max(max, r.bestCost());
                if (r.bestCost() < min) {
                    min = r.bestCost();
                    best = r;
                }
            }
            double mean = sum / runs.size(), var = 0;
            for (RunResult<?> r : runs)
                var += (r.bestCost() - mean) * (r.bestCost() - mean);
            Object solution = best.bestSolution();
            LayoutEvaluation e = experimentCase.getProblem() instanceof DiscreteWFLOP
                    ? ((DiscreteWFLOP) experimentCase.getProblem()).evaluate((CellLayout) solution)
                    : problem.evaluate((DoubleVector) solution);
            System.out.printf("  %-5s %10.6f %10.6f %10.6f %10.2e %14.4f %9.4f %10.2f%% %4d%n", experimentCase.getTitle(),
                    min, max, mean, Math.sqrt(var / runs.size()), e.power(), e.cost(), 100 * e.efficiency(), e.turbines());
            if (overallBest == null || e.objective() < overallBest.objective())
                overallBest = e;
        }

        @Override
        public void end() {
            System.out.println("  best layout (north up, X = turbine): " + overallBest);
            System.out.print(overallBest.render().replaceAll("(?m)^", "    "));
        }
    }
}
