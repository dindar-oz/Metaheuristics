package metaheuristic.pso;

import base.ContinuousProblem;
import base.InitialSolutionGenerator;
import base.OptimizationProblem;
import base.TerminalCondition;
import metaheuristic.AbstractMetaheuristic;
import metaheuristic.BasePIterationEvent;
import representation.AgeingIndividual;
import representation.DoubleVector;
import representation.ListPopulation;
import representation.base.Population;
import representation.base.Representation;
import util.random.RandUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Global-best particle swarm optimization for ContinuousProblems.
 * v = w*v + siP*rP*(personalBest - x) + siG*rG*(globalBest - x);  x = clamp(x + v)
 */
public class PSO extends AbstractMetaheuristic {

    private final double w;
    private final double siP;
    private final double siG;
    private final int swarmSize;

    private List<Particle> swarm;

    public PSO(double w, double siP, double siG, int swarmSize, TerminalCondition tc) {
        super(tc);
        this.w = w;
        this.siP = siP;
        this.siG = siG;
        this.swarmSize = swarmSize;
    }

    @Override
    protected void run(OptimizationProblem problem, InitialSolutionGenerator solutionGenerator) {
        if (!(problem instanceof ContinuousProblem))
            throw new IllegalArgumentException("PSO requires a ContinuousProblem.");
        ContinuousProblem cProblem = (ContinuousProblem) problem;

        swarm = new ArrayList<>(swarmSize);
        for (Representation r : solutionGenerator.generate(problem, swarmSize))
            swarm.add(new Particle(cProblem, (DoubleVector) r));

        while (!isTerminated()) {
            double[] globalBest = ((DoubleVector) getBestKnownSolution()).getValues();
            for (Particle p : swarm)
                p.update(cProblem, globalBest);

            iterationCount++;
            if (hasListeners()) {
                Population population = swarmAsPopulation();
                fireIterationEvent(new BasePIterationEvent(iterationCount, getNeighboringCount(),
                        getBestKnownCost(), getBestKnownSolution(), population));
            }
        }
    }

    private Population swarmAsPopulation() {
        Population population = new ListPopulation();
        for (Particle p : swarm)
            population.add(new AgeingIndividual(p.position, p.cost));
        return population;
    }

    @Override
    protected String defaultName() {
        return "PSO";
    }

    private class Particle {
        final DoubleVector position;
        final double[] velocity;
        double cost;
        double[] bestPosition;
        double bestCost;

        Particle(ContinuousProblem problem, DoubleVector position) {
            this.position = position;
            int n = problem.getDimensionCount();
            velocity = new double[n];
            for (int d = 0; d < n; d++) {
                double range = problem.getUpperBound(d) - problem.getLowerBound(d);
                velocity[d] = RandUtil.randDouble(-range / 2, range / 2);
            }
            cost = evaluate(problem, position);
            bestPosition = position.getValues().clone();
            bestCost = cost;
        }

        void update(ContinuousProblem problem, double[] globalBest) {
            double[] x = position.getValues();
            double rP = RandUtil.randDouble();
            double rG = RandUtil.randDouble();
            for (int d = 0; d < x.length; d++) {
                velocity[d] = w * velocity[d]
                        + siP * rP * (bestPosition[d] - x[d])
                        + siG * rG * (globalBest[d] - x[d]);
                x[d] = Math.max(problem.getLowerBound(d), Math.min(problem.getUpperBound(d), x[d] + velocity[d]));
            }

            cost = evaluate(problem, position);
            if (cost < bestCost) {
                bestCost = cost;
                bestPosition = x.clone();
            }
        }
    }
}
