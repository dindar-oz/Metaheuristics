package metaheuristic.ea;

import base.InitialSolutionGenerator;
import base.MutationOperator;
import base.OptimizationProblem;
import metaheuristic.AbstractMetaheuristic;
import metaheuristic.Params;
import metaheuristic.ea.base.*;
import metaheuristic.ea.crossover.SimpleCrossOverStrategy;
import metaheuristic.ea.parentselector.TournamentParentSelector;
import metaheuristic.ea.replacement.SteadyStateReplacement;
import metaheuristic.ea.victimselector.SimpleVictimSelector;
import metaheuristic.run.Metrics;
import metaheuristic.run.SearchContext;
import representation.base.Individual;
import representation.base.Population;
import representation.base.Representation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Evolutionary algorithm. Every generation:
 * parents are selected, crossover offsprings are offered through the ReplacementStrategy,
 * the MutationStrategy mutates the population, then every GenerationHook runs (immigration, ageing, ...).
 * <pre>
 * EA ea = EA.builder(isg).crossovers(new SimplePermutationCrossover()).mutations(new TwoOpt())
 *           .populationSize(100).hook(new ImmigrationHook(50, 10)).build();
 * </pre>
 */
public final class EA<S extends Representation<S>> extends AbstractMetaheuristic<S, OptimizationProblem<S>> {

    private final List<CrossOverOperator<S>> crossOverOperators;
    private final List<MutationOperator<S>> mutationOperators;
    private final InitialSolutionGenerator<S> generator;
    private final ParentSelector parentSelector;
    private final CrossOverStrategy crossOverStrategy;
    private final MutationStrategy mutationStrategy;
    private final ReplacementStrategy replacementStrategy;
    private final List<GenerationHook<S>> hooks;
    private final int populationSize;

    private EA(Builder<S> b) {
        super(b.name);
        this.crossOverOperators = Params.immutableCopy(b.crossOverOperators);
        this.mutationOperators = Params.immutableCopy(b.mutationOperators);
        if (crossOverOperators.isEmpty() && mutationOperators.isEmpty())
            throw new IllegalArgumentException("EA needs at least one crossover or mutation operator");
        this.generator = Params.required("generator", b.generator);
        this.parentSelector = Params.required("parentSelector", b.parentSelector);
        this.crossOverStrategy = Params.required("crossOverStrategy", b.crossOverStrategy);
        this.mutationStrategy = Params.required("mutationStrategy", b.mutationStrategy);
        this.replacementStrategy = Params.required("replacementStrategy", b.replacementStrategy);
        this.hooks = Params.immutableCopy(b.hooks);
        this.populationSize = Params.positive("populationSize", b.populationSize);
    }

    public static <S extends Representation<S>> Builder<S> builder(InitialSolutionGenerator<S> generator) {
        Builder<S> b = new Builder<>();
        b.generator = generator;
        return b;
    }

    @Override
    public void search(OptimizationProblem<S> problem, SearchContext<S> ctx) {
        Run run = new Run(problem, ctx);
        Population<S> population = new Population<>();
        for (int i = 0; i < populationSize && !ctx.shouldStop(); i++)
            population.add(run.createRandom());

        while (!ctx.shouldStop()) {
            run.generation++;
            if (!crossOverOperators.isEmpty()) {
                List<Individual<S>> parents = parentSelector.selectParents(population.individuals(), ctx.rng());
                List<Individual<S>> offsprings = crossOverStrategy.generateOffsprings(parents, crossOverOperators, run);
                replacementStrategy.replace(population, offsprings, run);
            }
            mutationStrategy.applyMutations(population, mutationOperators, run);
            for (GenerationHook<S> hook : hooks)
                hook.afterGeneration(population, run);

            ctx.iterationCompleted(Metrics.of(Metrics.MEAN_COST, population.meanCost()),
                    population.individuals().stream().map(Individual::getRepresentation).toList());
        }
    }

    @Override
    protected String parameters() {
        return "populationSize=" + populationSize + ", initial=" + Params.describe(generator) + ", crossovers=" + Params.describe(crossOverOperators)
                + ", mutations=" + Params.describe(mutationOperators) + ", parents=" + Params.describe(parentSelector)
                + ", " + Params.describe(crossOverStrategy) + ", " + Params.describe(mutationStrategy)
                + ", " + Params.describe(replacementStrategy) + (hooks.isEmpty() ? "" : ", hooks=" + Params.describe(hooks));
    }

    // The EAContext of one run
    private final class Run implements EAContext<S> {
        private final OptimizationProblem<S> problem;
        private final SearchContext<S> ctx;
        private long generation = 0;

        Run(OptimizationProblem<S> problem, SearchContext<S> ctx) {
            this.problem = problem;
            this.ctx = ctx;
        }

        @Override
        public SearchContext<S> search() {
            return ctx;
        }

        @Override
        public long generation() {
            return generation;
        }

        @Override
        public int populationSize() {
            return populationSize;
        }

        @Override
        public Individual<S> evaluate(S solution) {
            return new Individual<>(solution, ctx.evaluate(solution), generation);
        }

        @Override
        public Individual<S> createRandom() {
            return evaluate(generator.generate(problem, ctx.rng()));
        }

        @Override
        public void insert(Population<S> population, List<Individual<S>> newcomers) {
            replacementStrategy.replace(population, newcomers, this);
        }
    }

    public static final class Builder<S extends Representation<S>> {
        private String name = "EA";
        private final List<CrossOverOperator<S>> crossOverOperators = new ArrayList<>();
        private final List<MutationOperator<S>> mutationOperators = new ArrayList<>();
        private InitialSolutionGenerator<S> generator;
        private ParentSelector parentSelector = new TournamentParentSelector(3, 2);
        private CrossOverStrategy crossOverStrategy = new SimpleCrossOverStrategy(0.8);
        private MutationStrategy mutationStrategy = new SimpleMutationStrategy(0.8, 1);
        private ReplacementStrategy replacementStrategy = new SteadyStateReplacement(new SimpleVictimSelector());
        private final List<GenerationHook<S>> hooks = new ArrayList<>();
        private int populationSize = 50;

        private Builder() {
        }

        public Builder<S> name(String name) {
            this.name = name;
            return this;
        }


        @SafeVarargs
        public final Builder<S> crossovers(CrossOverOperator<S>... operators) {
            return crossovers(Arrays.asList(operators));
        }

        public Builder<S> crossovers(List<? extends CrossOverOperator<S>> operators) {
            crossOverOperators.addAll(operators);
            return this;
        }

        @SafeVarargs
        public final Builder<S> mutations(MutationOperator<S>... operators) {
            return mutations(Arrays.asList(operators));
        }

        public Builder<S> mutations(List<? extends MutationOperator<S>> operators) {
            mutationOperators.addAll(operators);
            return this;
        }

        // default: tournament(3, 2)
        public Builder<S> parentSelector(ParentSelector parentSelector) {
            this.parentSelector = parentSelector;
            return this;
        }

        // default: SimpleCrossOverStrategy(0.8)
        public Builder<S> crossOverStrategy(CrossOverStrategy crossOverStrategy) {
            this.crossOverStrategy = crossOverStrategy;
            return this;
        }

        // default: SimpleMutationStrategy(0.8, 1)
        public Builder<S> mutationStrategy(MutationStrategy mutationStrategy) {
            this.mutationStrategy = mutationStrategy;
            return this;
        }

        // default: steady state, worst individuals are replaced, no duplicates
        public Builder<S> replacementStrategy(ReplacementStrategy replacementStrategy) {
            this.replacementStrategy = replacementStrategy;
            return this;
        }

        // hooks run in the order they are added
        public Builder<S> hook(GenerationHook<S> hook) {
            hooks.add(hook);
            return this;
        }

        // default 50
        public Builder<S> populationSize(int populationSize) {
            this.populationSize = populationSize;
            return this;
        }

        public EA<S> build() {
            return new EA<>(this);
        }
    }
}
