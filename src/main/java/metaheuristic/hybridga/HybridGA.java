package metaheuristic.hybridga;

import base.InitialSolutionGenerator;
import base.MutationOperator;
import base.OptimizationProblem;
import metaheuristic.AbstractMetaheuristic;
import metaheuristic.Emigration;
import metaheuristic.Immigration;
import metaheuristic.Params;
import metaheuristic.SearchProcess;
import metaheuristic.SteppableMetaheuristic;
import metaheuristic.ea.base.CrossOverOperator;
import metaheuristic.ea.base.ParentSelector;
import metaheuristic.ea.base.VictimSelector;
import metaheuristic.ea.parentselector.TournamentParentSelector;
import metaheuristic.ea.victimselector.RouletteWheelVictimSelector;
import metaheuristic.run.Metrics;
import metaheuristic.run.SearchContext;
import metaheuristic.sa.CoolingSchedule;
import metaheuristic.sa.GeometricCooling;
import representation.base.Individual;
import representation.base.Representation;
import util.random.RNG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The hybrid genetic algorithm of the research code (research/ga/pecto/PECTOGA), on the framework's classes. It is
 * written for any representation; {@link PECTOHybridGA} sets it up with the research operators for PECTO.
 * <p>
 * The population is first filled from the {@code sources}, each in proportion to its share. Every generation then:
 * <ol>
 * <li><b>Parents.</b> With probability {@code primaryProbability} the primary selector picks the parents, the
 *     secondary otherwise. By default these are a roulette wheel on 1 / cost ({@link InverseCostParentSelector}) and
 *     a tournament, both selecting k parents, with k from the population size as in the research code (5 up to 40
 *     individuals, 7 up to 80, 9 up to 120, 10 up to 160, 11 above).</li>
 * <li><b>Crossover.</b> Every pair of parents is crossed with probability {@code crossoverRate}.</li>
 * <li><b>Stagnation</b> (if {@code stagnationPeriod} &gt; 0). If the best cost in the population has not improved
 *     for {@code stagnationPeriod} generations, the next source in turn sends ceil(N x {@code migrantShare}) migrants, each replacing one of the
 *     old individuals ranked just after the oldest N x {@code migrantShare}; the mutation rate grows by the factor
 *     {@code mutationRateGrowth} (within [{@code minMutationRate}, {@code maxMutationRate}]), and every individual
 *     but the oldest N x {@code migrantShare} is perturbed once. The offspring are then not mutated. Otherwise, every
 *     offspring is mutated with the current mutation rate.</li>
 * <li><b>Hybrid refinement</b> (if {@code hybrid}). Every offspring is improved by a first-improvement local search
 *     over up to {@code localSearchSteps} random neighbors, and the best individual of the population by simulated
 *     annealing (from {@code initialTemperature} down to {@code minimumTemperature}, {@code innerLoopLength}
 *     neighbors per temperature), which it is replaced with the best solution of. Both draw their neighbors from the
 *     {@code refinement} operators.</li>
 * <li><b>Replacement.</b> Every offspring replaces a victim drawn with probability proportional to its cost among
 *     the individuals at least {@code minVictimAge} generations old, or among all if there are fewer of them than
 *     offspring. Offspring beyond the population size are dropped.</li>
 * </ol>
 * There is no elitism: the best individual can be replaced, but the run keeps the best solution ever evaluated.
 * Every solution is evaluated through the search context, the refinement neighbors included. Individuals keep their
 * birth generation when they are mutated or refined, so their age counts from when they entered the population.
 * <p>
 * A run can also exchange solutions with other runs (metaheuristic.island.IslandModel): it offers its population, and
 * migrants take the places of duplicates first, then of the oldest individuals, never of the best one; migrants that
 * are already members are left out. Migrants are born when they arrive, so {@code minVictimAge} protects them as it
 * protects offspring.
 */
public final class HybridGA<S extends Representation<S>> extends AbstractMetaheuristic<S, OptimizationProblem<S>>
        implements SteppableMetaheuristic<S, OptimizationProblem<S>> {

    /** The current mutation rate of the offspring, a metric of every generation. */
    public static final String MUTATION_RATE = "mutationRate";
    /** The number of stagnation responses (immigration and perturbation) so far, a metric of every generation. */
    public static final String IMMIGRATIONS = "immigrations";

    static final int MIGRANT_SLOTS = 3;     // a migrant replaces one of the 3 individuals after the spared oldest ones
    private static final VictimSelector VICTIMS = new RouletteWheelVictimSelector();

    private final List<Source<S>> sources;
    private final CrossOverOperator<S> crossover;
    private final MutationOperator<S> mutation;
    private final MutationOperator<S> perturbation;
    private final List<MutationOperator<S>> refinement;
    private final int populationSize;
    private final ParentSelector primarySelector, secondarySelector;
    private final double primaryProbability;
    private final double crossoverRate;
    private final double initialMutationRate, minMutationRate, maxMutationRate, mutationRateGrowth;
    private final int stagnationPeriod;
    private final double migrantShare;
    private final boolean immigration, perturbOnStagnation;
    private final int minVictimAge;
    private final boolean hybrid;
    private final int localSearchSteps;
    private final double initialTemperature, minimumTemperature;
    private final CoolingSchedule cooling;
    private final int innerLoopLength;

    private HybridGA(Builder<S> b) {
        super(b.name);
        sources = Params.nonEmpty("sources", b.sources);
        crossover = Params.required("crossover", b.crossover);
        mutation = Params.required("mutation", b.mutation);
        perturbation = Params.required("perturbation", b.perturbation);
        hybrid = b.hybrid;
        refinement = hybrid ? Params.nonEmpty("refinement", b.refinement) : Params.immutableCopy(b.refinement);
        populationSize = Params.positive("populationSize", b.populationSize);
        int parents = b.parentCount > 0 ? b.parentCount : researchParentCount(populationSize);
        primarySelector = b.primarySelector != null ? b.primarySelector : new InverseCostParentSelector(parents);
        secondarySelector = b.secondarySelector != null ? b.secondarySelector : new TournamentParentSelector(parents, parents);
        primaryProbability = Params.probability("primaryProbability", b.primaryProbability);
        crossoverRate = Params.probability("crossoverRate", b.crossoverRate);
        minMutationRate = Params.probability("minMutationRate", b.minMutationRate);
        maxMutationRate = Params.probability("maxMutationRate", b.maxMutationRate);
        if (minMutationRate > maxMutationRate)
            throw new IllegalArgumentException("minMutationRate must not exceed maxMutationRate");
        initialMutationRate = Params.probability("mutationRate", b.mutationRate);
        mutationRateGrowth = Params.positive("mutationRateGrowth", b.mutationRateGrowth);
        stagnationPeriod = Params.nonNegative("stagnationPeriod", b.stagnationPeriod);
        migrantShare = Params.probability("migrantShare", b.migrantShare);
        immigration = b.immigration;
        perturbOnStagnation = b.perturbOnStagnation;
        minVictimAge = Params.nonNegative("minVictimAge", b.minVictimAge);
        localSearchSteps = Params.nonNegative("localSearchSteps", b.localSearchSteps);
        initialTemperature = Params.positive("initialTemperature", b.initialTemperature);
        minimumTemperature = Params.nonNegative("minimumTemperature", b.minimumTemperature);
        cooling = Params.required("cooling", b.cooling);
        innerLoopLength = Params.nonNegative("innerLoopLength", b.innerLoopLength);
    }

    // the number of parents the research code selects for a population of the given size
    static int researchParentCount(int populationSize) {
        if (populationSize <= 40)
            return 5;
        if (populationSize <= 80)
            return 7;
        if (populationSize <= 120)
            return 9;
        return populationSize <= 160 ? 10 : 11;
    }

    public static <S extends Representation<S>> Builder<S> builder(CrossOverOperator<S> crossover) {
        Builder<S> b = new Builder<>();
        b.crossover = crossover;
        return b;
    }

    // the initial population, evaluated; every step is one generation
    @Override
    public SearchProcess start(OptimizationProblem<S> problem, SearchContext<S> ctx) {
        Run run = new Run(problem, ctx);
        run.seed();
        return run;
    }

    @Override
    protected String parameters() {
        return "populationSize=" + populationSize + ", sources=" + Params.describe(sources)
                + ", crossover=" + Params.describe(crossover) + ", crossoverRate=" + crossoverRate
                + ", mutation=" + Params.describe(mutation) + ", mutationRate=" + initialMutationRate
                + " in [" + minMutationRate + ", " + maxMutationRate + "] x" + mutationRateGrowth
                + ", parents=" + Params.describe(primarySelector) + " (p=" + primaryProbability + ") or "
                + Params.describe(secondarySelector)
                + ", stagnationPeriod=" + stagnationPeriod + ", migrantShare=" + migrantShare
                + (immigration ? "" : " (no immigration)")
                + ", perturbation=" + (perturbOnStagnation ? Params.describe(perturbation) : "off")
                + ", minVictimAge=" + minVictimAge
                + ", hybrid=" + hybrid
                + (hybrid ? ", refinement=" + Params.describe(refinement) + ", localSearchSteps=" + localSearchSteps
                + ", t0=" + initialTemperature + ", tMin=" + minimumTemperature + ", cooling=" + cooling
                + ", innerLoop=" + innerLoopLength : "");
    }

    // a generator of initial solutions and migrants, with its share of the initial population
    private record Source<T extends Representation<T>>(InitialSolutionGenerator<T> generator, double share) {
        @Override
        public String toString() {
            return Params.describe(generator) + " x" + share;
        }
    }

    // The state of one run
    private final class Run implements SearchProcess, Emigration<S>, Immigration<S> {
        private final OptimizationProblem<S> problem;
        private final SearchContext<S> ctx;
        private final RNG rng;
        private final List<Individual<S>> population = new ArrayList<>(populationSize);
        private long generation = 0;
        private double mutationRate = initialMutationRate;
        private double previousBest = Double.POSITIVE_INFINITY;
        private int stagnation = 0;
        private int migrantTurn = 0;
        private int immigrations = 0;

        Run(OptimizationProblem<S> problem, SearchContext<S> ctx) {
            this.problem = problem;
            this.ctx = ctx;
            this.rng = ctx.rng();
        }

        private Individual<S> evaluate(S solution, long birthGeneration) {
            return new Individual<>(solution, ctx.evaluate(solution), birthGeneration);
        }

        // the initial population, source by source
        void seed() {
            int[] counts = sourceCounts();
            for (int s = 0; s < sources.size(); s++)
                for (int i = 0; i < counts[s]; i++) {
                    if (ctx.shouldStop())
                        return;
                    population.add(evaluate(sources.get(s).generator().generate(problem, rng), generation));
                }
        }

        // the number of initial individuals of every source: its rounded share, the first source taking the rest
        private int[] sourceCounts() {
            double total = 0;
            for (Source<S> source : sources)
                total += source.share();
            int[] counts = new int[sources.size()];
            int remaining = populationSize;
            for (int s = sources.size() - 1; s > 0; s--) {
                counts[s] = Math.min(remaining, (int) Math.round(populationSize * sources.get(s).share() / total));
                remaining -= counts[s];
            }
            counts[0] = remaining;
            return counts;
        }

        @Override
        public void step() {
            ParentSelector selector = rng.rollDice(primaryProbability) ? primarySelector : secondarySelector;
            List<Individual<S>> parents = selector.selectParents(population, rng);
            List<Individual<S>> offspring = new ArrayList<>();
            for (int i = 0; i < parents.size(); i++)
                for (int j = i + 1; j < parents.size(); j++)
                    if (rng.rollDice(crossoverRate))
                        for (S child : crossover.apply(parents.get(i).getRepresentation(), parents.get(j).getRepresentation(), rng))
                            offspring.add(evaluate(child, generation));

            if (stagnationPeriod > 0 && stagnated())
                respondToStagnation();
            else
                mutate(offspring);

            if (hybrid) {
                for (int i = 0; i < offspring.size() && !ctx.shouldStop(); i++)
                    offspring.set(i, localSearch(offspring.get(i)));
                annealBest();
            }
            replace(offspring);

            ctx.iterationCompleted(Metrics.of(Metrics.MEAN_COST, meanCost(), MUTATION_RATE, mutationRate,
                    IMMIGRATIONS, immigrations), population.stream().map(Individual::getRepresentation).toList());
            generation++;
        }

        @Override
        public boolean finished() {
            return ctx.shouldStop();
        }

        @Override
        public List<Individual<S>> emigrants() {
            return List.copyOf(population);
        }

        @Override
        public List<S> residents() {
            return population.stream().map(Individual::getRepresentation).toList();
        }

        @Override
        public void immigrate(List<Individual<S>> immigrants) {
            Set<S> present = new HashSet<>(residents());
            List<Integer> victims = immigrationVictims();
            int v = 0;
            for (Individual<S> m : immigrants)
                if (v < victims.size() && present.add(m.getRepresentation()))
                    population.set(victims.get(v++), new Individual<>(m.getRepresentation(), m.getCost(), generation));
        }

        // the indices immigrants take, in turn: repeats of earlier members (the best counts as the first), then the
        // others oldest first, the costlier first among equally old; never the best individual (the first cheapest)
        private List<Integer> immigrationVictims() {
            int best = 0;
            for (int i = 1; i < population.size(); i++)
                if (population.get(i).getCost() < population.get(best).getCost())
                    best = i;
            Set<S> seen = new HashSet<>();
            if (!population.isEmpty())
                seen.add(population.get(best).getRepresentation());
            List<Integer> repeats = new ArrayList<>(), others = new ArrayList<>();
            for (int i = 0; i < population.size(); i++)
                if (i != best)
                    (seen.add(population.get(i).getRepresentation()) ? others : repeats).add(i);
            others.sort(Comparator.<Integer>comparingLong(i -> population.get(i).getBirthGeneration())
                    .thenComparingDouble(i -> -population.get(i).getCost()));
            repeats.addAll(others);
            return repeats;
        }

        // true when the best cost in the population has not improved for stagnationPeriod generations
        private boolean stagnated() {
            double best = Double.POSITIVE_INFINITY;
            for (Individual<S> i : population)
                best = Math.min(best, i.getCost());
            if (best < previousBest) {
                previousBest = best;
                stagnation = 0;
                return false;
            }
            return ++stagnation >= stagnationPeriod;
        }

        private void respondToStagnation() {
            int spared = (int) (populationSize * migrantShare);
            int migrants = (int) Math.ceil(populationSize * migrantShare);
            InitialSolutionGenerator<S> source = sources.get(migrantTurn).generator();
            for (int m = 0; m < migrants && immigration && !ctx.shouldStop(); m++) {
                sortOldestFirst();
                int slot = Math.min(population.size() - 1, spared + rng.randInt(MIGRANT_SLOTS));
                population.set(slot, evaluate(source.generate(problem, rng), generation));
            }
            migrantTurn = (migrantTurn + 1) % sources.size();
            mutationRate = Math.max(minMutationRate, Math.min(maxMutationRate, mutationRate * mutationRateGrowth));
            stagnation = 0;
            immigrations++;

            if (perturbOnStagnation)
                sortOldestFirst();
            for (int i = spared; i < population.size() && perturbOnStagnation && !ctx.shouldStop(); i++) {
                Individual<S> old = population.get(i);
                population.set(i, evaluate(perturbation.apply(old.getRepresentation(), rng), old.getBirthGeneration()));
            }
        }

        // stable, so that individuals of the same age keep their order
        private void sortOldestFirst() {
            population.sort(Comparator.comparingLong(Individual::getBirthGeneration));
        }

        private void mutate(List<Individual<S>> offspring) {
            for (int i = 0; i < offspring.size(); i++)
                if (mutationRate > 0 && rng.rollDice(mutationRate))     // no draw at rate 0 (mutation off)
                    offspring.set(i, evaluate(mutation.apply(offspring.get(i).getRepresentation(), rng), generation));
        }

        // the first improving one of up to localSearchSteps random neighbors, or the individual itself
        private Individual<S> localSearch(Individual<S> individual) {
            for (int step = 0; step < localSearchSteps && !ctx.shouldStop(); step++) {
                S neighbor = randomRefinement().apply(individual.getRepresentation(), rng);
                double cost = ctx.evaluate(neighbor);
                if (cost < individual.getCost())
                    return new Individual<>(neighbor, cost, individual.getBirthGeneration());
            }
            return individual;
        }

        // replaces the best individual with the best solution of a simulated annealing that starts from it
        private void annealBest() {
            if (population.isEmpty() || innerLoopLength == 0)
                return;
            int index = 0;
            for (int i = 1; i < population.size(); i++)
                if (population.get(i).getCost() < population.get(index).getCost())
                    index = i;
            Individual<S> current = population.get(index), best = current;
            for (double t = initialTemperature; t > minimumTemperature && !ctx.shouldStop(); t = cooling.updateTemperature(t))
                for (int i = 0; i < innerLoopLength && !ctx.shouldStop(); i++) {
                    S neighbor = randomRefinement().apply(current.getRepresentation(), rng);
                    double cost = ctx.evaluate(neighbor);
                    if (cost < current.getCost() || rng.rollDice(Math.exp((current.getCost() - cost) / t))) {
                        current = new Individual<>(neighbor, cost, current.getBirthGeneration());
                        if (cost < best.getCost())
                            best = current;
                    }
                }
            population.set(index, best);
        }

        private MutationOperator<S> randomRefinement() {
            return refinement.get(rng.randInt(refinement.size()));
        }

        // every offspring takes the place of a victim; old individuals are preferred as victims, costly ones more likely
        private void replace(List<Individual<S>> offspring) {
            if (offspring.isEmpty())
                return;
            List<Individual<S>> candidates = new ArrayList<>();
            for (Individual<S> i : population)
                if (i.getAge(generation) >= minVictimAge)
                    candidates.add(i);
            if (candidates.size() < offspring.size())
                candidates = population;
            List<Individual<S>> victims = VICTIMS.selectVictims(candidates, offspring.size(), rng);
            for (int v = 0; v < victims.size(); v++)
                population.set(indexOf(victims.get(v)), offspring.get(v));
        }

        // by identity: equal solutions may be different individuals
        private int indexOf(Individual<S> individual) {
            for (int i = 0; i < population.size(); i++)
                if (population.get(i) == individual)
                    return i;
            throw new IllegalStateException("the individual is not in the population");
        }

        private double meanCost() {
            double sum = 0;
            for (Individual<S> i : population)
                sum += i.getCost();
            return population.isEmpty() ? Double.NaN : sum / population.size();
        }
    }

    public static final class Builder<S extends Representation<S>> {
        private String name = "HybridGA";
        private final List<Source<S>> sources = new ArrayList<>();
        private CrossOverOperator<S> crossover;
        private MutationOperator<S> mutation;
        private MutationOperator<S> perturbation;
        private final List<MutationOperator<S>> refinement = new ArrayList<>();
        private int populationSize = 40;
        private int parentCount = 0;
        private ParentSelector primarySelector, secondarySelector;
        private double primaryProbability = 0.75;
        private double crossoverRate = 0.75;
        private double mutationRate = 0.05;
        private double minMutationRate = 0.05;
        private double maxMutationRate = 0.75;
        private double mutationRateGrowth = 1.1;
        private int stagnationPeriod = 100;
        private double migrantShare = 0.1;
        private boolean immigration = true;
        private boolean perturbOnStagnation = true;
        private int minVictimAge = 3;
        private boolean hybrid = true;
        private int localSearchSteps = 10;
        private double initialTemperature = 25;
        private double minimumTemperature = 0.1;
        private CoolingSchedule cooling = new GeometricCooling(0.95);
        private int innerLoopLength = 1;

        private Builder() {
        }

        public Builder<S> name(String name) {
            this.name = name;
            return this;
        }

        // adds a generator of initial solutions and migrants, with its share of the initial population; migrants
        // come from the sources in turn, in the order they are added
        public Builder<S> source(InitialSolutionGenerator<S> generator, double share) {
            sources.add(new Source<>(Params.required("generator", generator), Params.positive("share", share)));
            return this;
        }

        // removes the sources added so far
        public Builder<S> clearSources() {
            sources.clear();
            return this;
        }

        public Builder<S> crossover(CrossOverOperator<S> crossover) {
            this.crossover = crossover;
            return this;
        }

        // applied to the offspring with the current mutation rate
        public Builder<S> mutation(MutationOperator<S> mutation) {
            this.mutation = mutation;
            return this;
        }

        // applied to most of the population when it stagnates
        public Builder<S> perturbation(MutationOperator<S> perturbation) {
            this.perturbation = perturbation;
            return this;
        }

        // the neighbors of the local search and the simulated annealing (replaces the operators set so far)
        @SafeVarargs
        public final Builder<S> refinement(MutationOperator<S>... operators) {
            refinement.clear();
            refinement.addAll(Arrays.asList(operators));
            return this;
        }

        // default 40
        public Builder<S> populationSize(int populationSize) {
            this.populationSize = populationSize;
            return this;
        }

        // parents of the default selectors; default (0): 5 to 11 by population size, as in the research code
        public Builder<S> parentCount(int parentCount) {
            this.parentCount = parentCount;
            return this;
        }

        // default: roulette on 1 / cost with probability 0.75, tournament otherwise
        public Builder<S> parentSelectors(ParentSelector primary, ParentSelector secondary, double primaryProbability) {
            this.primarySelector = primary;
            this.secondarySelector = secondary;
            this.primaryProbability = primaryProbability;
            return this;
        }

        // probability that a pair of parents is crossed; default 0.75
        public Builder<S> crossoverRate(double crossoverRate) {
            this.crossoverRate = crossoverRate;
            return this;
        }

        // initial mutation rate of the offspring; default 0.05
        public Builder<S> mutationRate(double mutationRate) {
            this.mutationRate = mutationRate;
            return this;
        }

        // the mutation rate is multiplied by growth at every stagnation and kept in [min, max]; default 1.1, [0.05, 0.75]
        public Builder<S> mutationRateAdaptation(double growth, double min, double max) {
            this.mutationRateGrowth = growth;
            this.minMutationRate = min;
            this.maxMutationRate = max;
            return this;
        }

        // generations without improvement of the population's best that trigger the stagnation response; default 100
        // (0: no stagnation response, so no migrants, perturbation or mutation-rate growth)
        public Builder<S> stagnationPeriod(int stagnationPeriod) {
            this.stagnationPeriod = stagnationPeriod;
            return this;
        }

        // share of the population that immigrates, and of the oldest spared by the perturbation; default 0.1
        public Builder<S> migrantShare(double migrantShare) {
            this.migrantShare = migrantShare;
            return this;
        }

        // whether stagnation brings migrants; default true. Off, the rest of the stagnation response is unchanged
        // (the oldest migrantShare are still spared by the perturbation)
        public Builder<S> immigration(boolean immigration) {
            this.immigration = immigration;
            return this;
        }

        // whether stagnation perturbs the population; default true
        public Builder<S> perturbationOnStagnation(boolean perturb) {
            this.perturbOnStagnation = perturb;
            return this;
        }

        // the age (in generations) from which an individual can be replaced by an offspring; default 3
        public Builder<S> minVictimAge(int minVictimAge) {
            this.minVictimAge = minVictimAge;
            return this;
        }

        // local search of the offspring and simulated annealing of the best individual; default true
        public Builder<S> hybrid(boolean hybrid) {
            this.hybrid = hybrid;
            return this;
        }

        // neighbors tried by the first-improvement local search of every offspring; default 10 (0: none)
        public Builder<S> localSearchSteps(int localSearchSteps) {
            this.localSearchSteps = localSearchSteps;
            return this;
        }

        // simulated annealing of the best individual of every generation; default 25 -> 0.1, geometric(0.95), 1
        // neighbor per temperature (an inner loop of 0 turns it off)
        public Builder<S> annealing(double initialTemperature, double minimumTemperature, CoolingSchedule cooling,
                                    int innerLoopLength) {
            this.initialTemperature = initialTemperature;
            this.minimumTemperature = minimumTemperature;
            this.cooling = cooling;
            this.innerLoopLength = innerLoopLength;
            return this;
        }

        public HybridGA<S> build() {
            return new HybridGA<>(this);
        }
    }
}
