package metaheuristic.ls;

import base.InitialSolutionGenerator;
import base.MutationOperator;
import base.OptimizationProblem;
import metaheuristic.AbstractMetaheuristic;
import metaheuristic.Params;
import metaheuristic.run.Metrics;
import metaheuristic.run.SearchContext;
import representation.base.Representation;
import util.random.RNG;

import java.util.List;

/**
 * Stochastic hill climbing: a random neighbor is accepted only if it is better.
 * Finishes by itself when maxNonImproving neighbors in a row fail to improve.
 */
public final class LocalSearch<S extends Representation<S>> extends AbstractMetaheuristic<S, OptimizationProblem<S>> {

    private final List<MutationOperator<S>> operators;
    private final InitialSolutionGenerator<S> generator;
    private final int maxNonImproving;

    private LocalSearch(Builder<S> b) {
        super(b.name);
        this.operators = Params.nonEmpty("operators", b.operators);
        this.generator = Params.required("generator", b.generator);
        this.maxNonImproving = Params.positive("maxNonImproving", b.maxNonImproving);
    }

    public static <S extends Representation<S>> Builder<S> builder(List<MutationOperator<S>> operators, InitialSolutionGenerator<S> generator) {
        Builder<S> b = new Builder<>();
        b.operators = operators;
        b.generator = generator;
        return b;
    }

    @Override
    public void search(OptimizationProblem<S> problem, SearchContext<S> ctx) {
        RNG rng = ctx.rng();
        S current = generator.generate(problem, rng);
        double currentCost = ctx.evaluate(current);

        int nonImproving = 0;
        while (nonImproving < maxNonImproving && !ctx.shouldStop()) {
            MutationOperator<S> nf = operators.get(rng.randInt(operators.size()));
            S neighbor = nf.apply(current, rng);
            double neighborCost = ctx.evaluate(neighbor);

            if (neighborCost < currentCost) {
                current = neighbor;
                currentCost = neighborCost;
                nonImproving = 0;
            } else
                nonImproving++;

            ctx.iterationCompleted(Metrics.of(Metrics.CURRENT_COST, currentCost));
        }
    }

    @Override
    protected String parameters() {
        return "operators=" + Params.describe(operators) + ", maxNonImproving=" + maxNonImproving;
    }

    public static final class Builder<S extends Representation<S>> {
        private String name = "LocalSearch";
        private List<MutationOperator<S>> operators;
        private InitialSolutionGenerator<S> generator;
        private int maxNonImproving = 1000;

        private Builder() {
        }

        public Builder<S> name(String name) {
            this.name = name;
            return this;
        }



        // consecutive non-improving neighbors after which the search ends; default: 1000
        public Builder<S> maxNonImproving(int maxNonImproving) {
            this.maxNonImproving = maxNonImproving;
            return this;
        }

        public LocalSearch<S> build() {
            return new LocalSearch<>(this);
        }
    }
}
