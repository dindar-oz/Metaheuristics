package metaheuristic.pso;

import base.ContinuousProblem;
import base.InitialSolutionGenerator;
import metaheuristic.AbstractMetaheuristic;
import metaheuristic.Params;
import metaheuristic.run.Metrics;
import metaheuristic.run.SearchContext;
import representation.DoubleVector;
import util.random.RNG;

import java.util.ArrayList;
import java.util.List;

/**
 * Global-best particle swarm optimization for ContinuousProblems.
 * v = inertia*v + cognitive*rP*(personalBest - x) + social*rG*(globalBest - x);  x = clamp(x + v)
 * Defaults are the standard constriction coefficients (0.7298, 1.49618, 1.49618).
 */
public final class PSO extends AbstractMetaheuristic<DoubleVector, ContinuousProblem> {

    private final double inertia;
    private final double cognitive;
    private final double social;
    private final int swarmSize;
    private final InitialSolutionGenerator<DoubleVector> generator; // null: uniform in the bounds

    private PSO(Builder b) {
        super(b.name);
        this.inertia = Params.nonNegative("inertia", b.inertia);
        this.cognitive = Params.nonNegative("cognitive", b.cognitive);
        this.social = Params.nonNegative("social", b.social);
        this.swarmSize = Params.positive("swarmSize", b.swarmSize);
        this.generator = b.generator;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void search(ContinuousProblem cProblem, SearchContext<DoubleVector> ctx) {
        RNG rng = ctx.rng();

        List<Particle> swarm = new ArrayList<>(swarmSize);
        for (int i = 0; i < swarmSize && !ctx.shouldStop(); i++)
            swarm.add(new Particle(cProblem, initialSolution(cProblem, rng), ctx));

        while (!ctx.shouldStop()) {
            double[] globalBest = ctx.bestSolution().getValues();
            double sum = 0;
            for (Particle p : swarm) {
                if (ctx.shouldStop())
                    return;
                p.update(cProblem, globalBest, ctx);
                sum += p.cost;
            }
            ctx.iterationCompleted(Metrics.of(Metrics.MEAN_COST, sum / swarm.size()));
        }
    }

    private DoubleVector initialSolution(ContinuousProblem problem, RNG rng) {
        return generator == null ? problem.randomSolution(rng) : generator.generate(problem, rng);
    }

    @Override
    protected String parameters() {
        return "inertia=" + inertia + ", cognitive=" + cognitive + ", social=" + social + ", swarmSize=" + swarmSize;
    }

    // Run state of one particle; lives only inside search()
    private final class Particle {
        final double[] position;
        final double[] velocity;
        double cost;
        double[] bestPosition;
        double bestCost;

        Particle(ContinuousProblem problem, DoubleVector initial, SearchContext<DoubleVector> ctx) {
            position = initial.getValues().clone();
            int n = problem.getDimensionCount();
            velocity = new double[n];
            for (int d = 0; d < n; d++) {
                double range = problem.getUpperBound(d) - problem.getLowerBound(d);
                velocity[d] = ctx.rng().randDouble(-range / 2, range / 2);
            }
            cost = ctx.evaluate(new DoubleVector(position.clone()));
            bestPosition = position.clone();
            bestCost = cost;
        }

        void update(ContinuousProblem problem, double[] globalBest, SearchContext<DoubleVector> ctx) {
            RNG rng = ctx.rng();
            double rP = rng.randDouble();
            double rG = rng.randDouble();
            for (int d = 0; d < position.length; d++) {
                velocity[d] = inertia * velocity[d]
                        + cognitive * rP * (bestPosition[d] - position[d])
                        + social * rG * (globalBest[d] - position[d]);
                position[d] = Math.max(problem.getLowerBound(d),
                        Math.min(problem.getUpperBound(d), position[d] + velocity[d]));
            }

            cost = ctx.evaluate(new DoubleVector(position.clone()));
            if (cost < bestCost) {
                bestCost = cost;
                bestPosition = position.clone();
            }
        }
    }

    public static final class Builder {
        private String name = "PSO";
        private double inertia = 0.7298;
        private double cognitive = 1.49618;
        private double social = 1.49618;
        private int swarmSize = 40;
        private InitialSolutionGenerator<DoubleVector> generator = null;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        // w; default 0.7298
        public Builder inertia(double inertia) {
            this.inertia = inertia;
            return this;
        }

        // attraction to the particle's own best (c1); default 1.49618
        public Builder cognitive(double cognitive) {
            this.cognitive = cognitive;
            return this;
        }

        // attraction to the swarm's best (c2); default 1.49618
        public Builder social(double social) {
            this.social = social;
            return this;
        }

        // default 40
        public Builder swarmSize(int swarmSize) {
            this.swarmSize = swarmSize;
            return this;
        }

        // default: uniform inside the problem's bounds
        public Builder generator(InitialSolutionGenerator<DoubleVector> generator) {
            this.generator = generator;
            return this;
        }

        public PSO build() {
            return new PSO(this);
        }
    }
}
