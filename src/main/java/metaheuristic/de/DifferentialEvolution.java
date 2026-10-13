package metaheuristic.de;

import base.ContinuousProblem;
import base.InitialSolutionGenerator;
import metaheuristic.AbstractMetaheuristic;
import metaheuristic.Params;
import metaheuristic.run.Metrics;
import metaheuristic.run.SearchContext;
import representation.DoubleVector;
import util.random.RNG;

/**
 * Classic DE/rand/1/bin. Mutants leaving the search box are corrected by the BoundaryHandler.
 */
public final class DifferentialEvolution extends AbstractMetaheuristic<DoubleVector, ContinuousProblem> {

    private final BoundaryHandler boundaryHandler;
    private final int populationSize;
    private final double crossoverRate;
    private final double differentialWeight;
    private final InitialSolutionGenerator<DoubleVector> generator; // null: uniform in the bounds

    private DifferentialEvolution(Builder b) {
        super(b.name);
        this.boundaryHandler = Params.required("boundaryHandler", b.boundaryHandler);
        this.populationSize = Params.positive("populationSize", b.populationSize);
        this.crossoverRate = Params.probability("crossoverRate", b.crossoverRate);
        this.differentialWeight = Params.positive("differentialWeight", b.differentialWeight);
        this.generator = b.generator;
        if (populationSize < 4)
            throw new IllegalArgumentException("DE needs a population of at least 4");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void search(ContinuousProblem cProblem, SearchContext<DoubleVector> ctx) {
        RNG rng = ctx.rng();

        double[][] population = new double[populationSize][];
        double[] costs = new double[populationSize];
        for (int i = 0; i < populationSize; i++) {
            DoubleVector v = initialSolution(cProblem, rng);
            population[i] = v.getValues().clone();
            costs[i] = ctx.evaluate(v);
        }

        while (!ctx.shouldStop()) {
            double[][] next = new double[populationSize][];
            double sum = 0;
            for (int i = 0; i < populationSize; i++) {
                double[] mutant = mutate(population, i, rng);
                boundaryHandler.handle(cProblem, mutant, population[i], rng);
                double[] trial = crossOver(mutant, population[i], rng);

                double trialCost = ctx.evaluate(new DoubleVector(trial.clone()));
                if (trialCost < costs[i]) {
                    next[i] = trial;
                    costs[i] = trialCost;
                } else
                    next[i] = population[i];
                sum += costs[i];
            }
            population = next;
            ctx.iterationCompleted(Metrics.of(Metrics.MEAN_COST, sum / populationSize));
        }
    }

    // v = a + F * (b - c) for three distinct random individuals other than index
    private double[] mutate(double[][] population, int index, RNG rng) {
        int a, b, c;
        do { a = rng.randInt(populationSize); } while (a == index);
        do { b = rng.randInt(populationSize); } while (b == index || b == a);
        do { c = rng.randInt(populationSize); } while (c == index || c == a || c == b);

        double[] mutant = new double[population[a].length];
        for (int d = 0; d < mutant.length; d++)
            mutant[d] = population[a][d] + differentialWeight * (population[b][d] - population[c][d]);
        return mutant;
    }

    // Binomial crossover; at least one dimension always comes from the mutant
    private double[] crossOver(double[] mutant, double[] target, RNG rng) {
        double[] trial = new double[mutant.length];
        int randIndex = rng.randInt(mutant.length);
        for (int d = 0; d < mutant.length; d++)
            trial[d] = (d == randIndex || rng.rollDice(crossoverRate)) ? mutant[d] : target[d];
        return trial;
    }

    private DoubleVector initialSolution(ContinuousProblem problem, RNG rng) {
        return generator == null ? problem.randomSolution(rng) : generator.generate(problem, rng);
    }

    @Override
    protected String parameters() {
        return "boundary=" + Params.describe(boundaryHandler) + ", populationSize=" + populationSize
                + ", CR=" + crossoverRate + ", F=" + differentialWeight;
    }

    public static final class Builder {
        private String name = "DE";
        private BoundaryHandler boundaryHandler = new SaturationBH();
        private int populationSize = 50;
        private double crossoverRate = 0.9;
        private double differentialWeight = 0.8;
        private InitialSolutionGenerator<DoubleVector> generator = null;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        // default: SaturationBH
        public Builder boundaryHandler(BoundaryHandler boundaryHandler) {
            this.boundaryHandler = boundaryHandler;
            return this;
        }

        // default 50, at least 4
        public Builder populationSize(int populationSize) {
            this.populationSize = populationSize;
            return this;
        }

        // CR; default 0.9
        public Builder crossoverRate(double crossoverRate) {
            this.crossoverRate = crossoverRate;
            return this;
        }

        // F; default 0.8
        public Builder differentialWeight(double differentialWeight) {
            this.differentialWeight = differentialWeight;
            return this;
        }

        // default: uniform inside the problem's bounds
        public Builder generator(InitialSolutionGenerator<DoubleVector> generator) {
            this.generator = generator;
            return this;
        }

        public DifferentialEvolution build() {
            return new DifferentialEvolution(this);
        }
    }
}
