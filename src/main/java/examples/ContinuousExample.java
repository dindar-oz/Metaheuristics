package examples;

import base.ContinuousProblem;
import base.RandomDoubleVectorSG;
import base.TerminalCondition;
import base.terminalcondition.NeighboringBasedTC;
import experiments.ConsoleERH;
import experiments.Experiment;
import experiments.ExperimentCase;
import metaheuristic.de.*;
import metaheuristic.pso.PSO;
import representation.DoubleVector;
import representation.base.Representation;

import java.util.Arrays;
import java.util.Collections;

/**
 * Compares DE with different boundary handlers and PSO on the 20-dimensional Rastrigin function.
 */
public class ContinuousExample {

    // f(x) = 10n + sum(x_i^2 - 10 cos(2 pi x_i)), x in [-5.12, 5.12]^n, minimum 0 at the origin
    static class Rastrigin implements ContinuousProblem {
        private final int dimension;

        Rastrigin(int dimension) {
            this.dimension = dimension;
        }

        @Override
        public double cost(Representation r) {
            double sum = 10 * dimension;
            for (double x : ((DoubleVector) r).getValues())
                sum += x * x - 10 * Math.cos(2 * Math.PI * x);
            return sum;
        }

        @Override
        public int getDimensionCount() {
            return dimension;
        }

        @Override
        public double getUpperBound(int d) {
            return 5.12;
        }

        @Override
        public double getLowerBound(int d) {
            return -5.12;
        }
    }

    static TerminalCondition budget() {
        return new NeighboringBasedTC(100000);
    }

    public static void main(String[] args) {
        Rastrigin problem = new Rastrigin(20);
        RandomDoubleVectorSG isg = new RandomDoubleVectorSG();

        new Experiment(Arrays.asList(
                new ExperimentCase("DE-Saturation", new DifferentialEvolution(new SaturationBH(), budget(), 50, 0.9, 0.5), problem, isg),
                new ExperimentCase("DE-Mirror", new DifferentialEvolution(new MirroringBH(), budget(), 50, 0.9, 0.5), problem, isg),
                new ExperimentCase("DE-Midpoint", new DifferentialEvolution(new MidpointBH(), budget(), 50, 0.9, 0.5), problem, isg),
                new ExperimentCase("DE-Random", new DifferentialEvolution(new RandomBH(), budget(), 50, 0.9, 0.5), problem, isg),
                new ExperimentCase("PSO", new PSO(0.5034, 1.0519, 2.3403, 50, budget()), problem, isg)),
                5, Collections.singletonList(new ConsoleERH())).run();
    }
}
