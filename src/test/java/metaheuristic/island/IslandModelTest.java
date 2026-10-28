package metaheuristic.island;

import base.OptimizationProblem;
import base.RandomPermutationSG;
import metaheuristic.Emigration;
import metaheuristic.Immigration;
import metaheuristic.SearchProcess;
import metaheuristic.SteppableMetaheuristic;
import metaheuristic.ea.crossover.SimplePermutationCrossover;
import metaheuristic.hybridga.HybridGA;
import metaheuristic.hybridga.PECTOHybridGA;
import metaheuristic.run.Progress;
import metaheuristic.run.ProgressSnapshot;
import metaheuristic.run.RunResult;
import metaheuristic.run.Runner;
import metaheuristic.run.SearchContext;
import metaheuristic.run.SearchListener;
import metaheuristic.termination.EvaluationBasedTC;
import operators.TwoOpt;
import org.junit.jupiter.api.Test;
import problems.pecto.PECTOInstances;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.aco.PECTOACO;
import problems.pecto.island.PECTOIslands;
import problems.pecto.operators.RandomPECTOSG;
import problems.tsp.TSPProblem;
import representation.IntegerPermutation;
import representation.base.Individual;
import representation.base.Representation;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IslandModelTest {

    private static final PECTOProblem PROBLEM = PECTOInstances.random(10, 120, 5, 30, 3);

    private static <S extends Representation<S>, P extends OptimizationProblem<S>> RunResult<S> run(
            IslandModel<S, P> model, P problem, long seed, int evaluations, List<ProgressSnapshot> rounds) {
        SearchListener listener = new SearchListener() {
            @Override
            public void onProgress(ProgressSnapshot p) {
                rounds.add(p);
            }
        };
        return Runner.of(model).seed(seed).stopWhen(new EvaluationBasedTC(evaluations)).listener(listener).run(problem);
    }

    private static IslandModel<PECTOSolution, PECTOProblem> small(boolean channels) {
        PECTOACO aco = PECTOACO.builder().antCount(8).localSearchSteps(20).build();
        HybridGA<PECTOSolution> ga = PECTOHybridGA.builder(PROBLEM).populationSize(20).build();
        IslandModel.Builder<PECTOSolution, PECTOProblem> b = PECTOIslands.islands(aco, ga, 10);
        if (channels)
            b.channel(PECTOIslands.ACO, PECTOIslands.GA, PECTOIslands.acoToGa(0.001, 20), MigrantSelector.diverse(3, 0.1))
                    .channel(PECTOIslands.GA, PECTOIslands.ACO, MigrationTrigger.whenBetter(0.001), MigrantSelector.best(1));
        return b.build();
    }

    // ---- the model

    @Test
    void sameSeedSameRun() {
        IslandModel<PECTOSolution, PECTOProblem> model = small(true);
        List<ProgressSnapshot> first = new ArrayList<>(), second = new ArrayList<>();
        RunResult<PECTOSolution> a = run(model, PROBLEM, 5, 6000, first), b = run(model, PROBLEM, 5, 6000, second);
        assertEquals(a.bestCost(), b.bestCost());
        assertArrayEquals(a.bestSolution().toSequence(), b.bestSolution().toSequence());
        assertEquals(first.size(), second.size());
        for (int r = 0; r < first.size(); r++) {
            Map<String, Double> x = new java.util.LinkedHashMap<>(first.get(r).metrics()), y = new java.util.LinkedHashMap<>(second.get(r).metrics());
            x.keySet().removeIf(k -> k.endsWith(".millis"));
            y.keySet().removeIf(k -> k.endsWith(".millis"));
            assertEquals(x, y);
        }
    }

    @Test
    void everyEvaluationOfTheIslandsCountsForTheRun() {
        List<ProgressSnapshot> rounds = new ArrayList<>();
        RunResult<PECTOSolution> r = run(small(true), PROBLEM, 3, 6000, rounds);
        assertFalse(rounds.isEmpty());
        for (ProgressSnapshot p : rounds) {
            Map<String, Double> m = p.metrics();
            assertEquals(p.evaluations(), m.get("aco.evaluations") + m.get("ga.evaluations"));
            assertTrue(p.bestCost() <= Math.min(m.get("aco.bestCost"), m.get("ga.bestCost")));
            assertEquals(p.bestCost(), Math.min(m.get("aco.bestCost"), m.get("ga.bestCost")));
        }
        assertEquals(6000, r.evaluations());
        assertTrue(PROBLEM.isFeasible(r.bestSolution()));
        assertEquals(PROBLEM.cost(r.bestSolution()), r.bestCost());
    }

    @Test
    void channelsMigrateAndAPortfolioDoesNot() {
        List<ProgressSnapshot> rounds = new ArrayList<>();
        run(small(true), PROBLEM, 3, 8000, rounds);
        Map<String, Double> last = rounds.get(rounds.size() - 1).metrics();
        assertTrue(last.get("aco>ga.migrations") >= 1, last.toString());
        assertTrue(last.get("aco>ga.migrants") >= last.get("aco>ga.migrations"));
        assertTrue(last.get("aco>ga.migrants") <= 3 * last.get("aco>ga.migrations"));
        assertTrue(last.containsKey("ga>aco.migrations"));
        assertTrue(last.containsKey("aco.branchingFactor") && last.containsKey("ga.diversity"));

        rounds.clear();
        run(small(false), PROBLEM, 3, 8000, rounds);
        assertTrue(rounds.get(rounds.size() - 1).metrics().keySet().stream().noneMatch(k -> k.contains(">")));
    }

    @Test
    void theGAIsStepsTimesAsManyIterations() {
        List<ProgressSnapshot> rounds = new ArrayList<>();
        run(small(false), PROBLEM, 4, 8000, rounds);
        for (ProgressSnapshot p : rounds)
            assertEquals(10 * p.metrics().get("aco.iterations"), p.metrics().get("ga.iterations"));
    }

    @Test
    void islandsOfOneGenericAlgorithmOnAnotherProblem() {
        TSPProblem tsp = TSPProblem.randomEuclidean(30, 1);
        TwoOpt twoOpt = new TwoOpt();
        HybridGA<IntegerPermutation> ga = HybridGA.<IntegerPermutation>builder(new SimplePermutationCrossover<>())
                .source(new RandomPermutationSG(30), 1).mutation(twoOpt).perturbation(twoOpt).refinement(twoOpt)
                .populationSize(16).build();
        // a ring of three; permutations define no distance, so the diverse selector takes the cheapest
        IslandModel<IntegerPermutation, TSPProblem> ring = IslandModel.<IntegerPermutation, TSPProblem>builder()
                .island("a", ga, 5).island("b", ga, 5).island("c", ga, 5)
                .channel("a", "b", MigrationTrigger.every(10), MigrantSelector.diverse(2, 0.5))
                .channel("b", "c", MigrationTrigger.every(10), MigrantSelector.diverse(2, 0.5))
                .channel("c", "a", MigrationTrigger.every(10), MigrantSelector.diverse(2, 0.5))
                .build();
        List<ProgressSnapshot> rounds = new ArrayList<>();
        RunResult<IntegerPermutation> r = run(ring, tsp, 2, 5000, rounds);
        Map<String, Double> last = rounds.get(rounds.size() - 1).metrics();
        for (String c : List.of("a>b", "b>c", "c>a"))
            assertTrue(last.get(c + ".migrations") >= 1, c);
        assertEquals(tsp.cost(r.bestSolution()), r.bestCost());
    }

    @Test
    void anIslandThatCannotTakeInMigrantsIsRejected() {
        IslandModel<PECTOSolution, PECTOProblem> model = IslandModel.<PECTOSolution, PECTOProblem>builder()
                .island("aco", PECTOACO.builder().antCount(4).build(), 1)
                .island("random", new RandomSearch(), 1)
                .channel("aco", "random", MigrationTrigger.every(1), MigrantSelector.best(1))
                .build();
        assertThrows(IllegalStateException.class, () -> run(model, PROBLEM, 1, 2000, new ArrayList<>()));
    }

    @Test
    void rejectsInvalidConfigurations() {
        PECTOACO aco = PECTOACO.builder().build();
        assertThrows(IllegalArgumentException.class, () -> IslandModel.<PECTOSolution, PECTOProblem>builder().build());
        assertThrows(IllegalArgumentException.class, () -> IslandModel.<PECTOSolution, PECTOProblem>builder()
                .island("a", aco, 1).island("a", aco, 1));
        assertThrows(IllegalArgumentException.class, () -> IslandModel.<PECTOSolution, PECTOProblem>builder()
                .island("a.b", aco, 1));
        assertThrows(IllegalArgumentException.class, () -> IslandModel.<PECTOSolution, PECTOProblem>builder()
                .island("a", aco, 0));
        assertThrows(IllegalArgumentException.class, () -> IslandModel.<PECTOSolution, PECTOProblem>builder()
                .island("a", aco, 1).channel("a", "a", MigrationTrigger.every(1), MigrantSelector.best(1)));
        assertThrows(IllegalArgumentException.class, () -> IslandModel.<PECTOSolution, PECTOProblem>builder()
                .island("a", aco, 1).channel("a", "b", MigrationTrigger.every(1), MigrantSelector.best(1)));
        assertThrows(IllegalArgumentException.class, () -> MigrationTrigger.whenBetter(1));
        assertThrows(IllegalArgumentException.class, () -> MigrantSelector.diverse(0, 0.1));
    }

    // ---- triggers

    private record At(double bestCost, long iterationsSinceImprovement) implements Progress {
        public long iterations() { return 0; }
        public long evaluations() { return 0; }
        public long elapsedMillis() { return 0; }
        public long timeToBestMillis() { return 0; }
        public long evaluationsSinceImprovement() { return 0; }
        public Map<String, Double> metrics() { return Map.of(); }
    }

    @Test
    void triggers() {
        MigrationTrigger better = MigrationTrigger.whenBetter(0.01);
        assertTrue(better.fires(new At(98, 0), new At(100, 0), 0));
        assertFalse(better.fires(new At(99.5, 0), new At(100, 0), 0));       // not by more than 1 %
        assertTrue(better.fires(new At(100, 0), new At(Double.POSITIVE_INFINITY, 0), 0));
        assertTrue(MigrationTrigger.whenBetter(0).fires(new At(-11, 0), new At(-10, 0), 0));

        MigrationTrigger stuck = MigrationTrigger.stagnation(5);
        assertTrue(stuck.fires(new At(1, 0), new At(1, 5), 5));
        assertFalse(stuck.fires(new At(1, 0), new At(1, 4), 9));              // improved 4 iterations ago
        assertFalse(stuck.fires(new At(1, 0), new At(1, 9), 4));              // migrants 4 iterations ago
        assertTrue(MigrationTrigger.every(3).fires(new At(1, 0), new At(1, 0), 3));
        assertFalse(MigrationTrigger.never().fires(new At(0, 0), new At(1, 99), 99));
        assertTrue(better.or(stuck).fires(new At(1, 0), new At(1, 5), 5));
    }

    // ---- selectors

    private static List<Individual<PECTOSolution>> offered(int count, long seed) {
        RNG rng = new MersenneTwisterRNG(seed);
        List<Individual<PECTOSolution>> offered = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PECTOSolution s = new RandomPECTOSG(PROBLEM).generate(PROBLEM, rng);
            offered.add(new Individual<>(s, PROBLEM.cost(s)));
        }
        return offered;
    }

    @Test
    void bestSelectorTakesTheCheapestThatTheTargetLacks() {
        List<Individual<PECTOSolution>> offered = offered(6, 1);
        offered.add(offered.get(2));                                          // a repeat
        List<Individual<PECTOSolution>> sorted = new ArrayList<>(offered);
        sorted.sort(Individual.BY_COST);
        List<Individual<PECTOSolution>> chosen = MigrantSelector.best(2).select(offered,
                List.of(sorted.get(0).getRepresentation()));
        assertEquals(2, chosen.size());
        assertNotEquals(sorted.get(0).getRepresentation(), chosen.get(0).getRepresentation());
        assertNotEquals(chosen.get(0).getRepresentation(), chosen.get(1).getRepresentation());
        assertTrue(chosen.get(0).getCost() <= chosen.get(1).getCost());
        assertTrue(MigrantSelector.best(3).select(List.of(sorted.get(0)), List.of(sorted.get(0).getRepresentation())).isEmpty());
    }

    @Test
    void diverseSelectorStartsWithTheCheapestAndSpreadsWithinTheGap() {
        List<Individual<PECTOSolution>> offered = offered(12, 2);
        double cheapest = offered.stream().mapToDouble(Individual::getCost).min().orElseThrow();
        List<Individual<PECTOSolution>> chosen = MigrantSelector.diverse(4, 0.05).select(offered, List.of());
        assertEquals(cheapest, chosen.get(0).getCost());
        assertEquals(chosen.size(), new HashSet<>(chosen.stream().map(Individual::getRepresentation).toList()).size());
        for (Individual<PECTOSolution> c : chosen)
            assertTrue(c.getCost() <= cheapest * 1.05);
        // with a wide gap, the second is the farthest from the first
        List<Individual<PECTOSolution>> wide = MigrantSelector.diverse(2, 10).select(offered, List.of());
        PECTOSolution first = wide.get(0).getRepresentation();
        double farthest = offered.stream().mapToDouble(i -> i.getRepresentation().distanceTo(first)).max().orElseThrow();
        assertEquals(farthest, wide.get(1).getRepresentation().distanceTo(first));
    }

    // ---- the capabilities of the PECTO islands

    // evaluates directly, never stops
    private static final class Direct<S extends Representation<S>> implements SearchContext<S> {
        private final OptimizationProblem<S> problem;
        private final RNG rng;

        Direct(OptimizationProblem<S> problem, long seed) {
            this.problem = problem;
            this.rng = new MersenneTwisterRNG(seed);
        }

        public RNG rng() { return rng; }
        public double evaluate(S solution) { return problem.cost(solution); }
        public boolean shouldStop() { return false; }
        public void iterationCompleted(Map<String, Double> metrics, List<? extends S> population) { }
        public Progress progress() { throw new UnsupportedOperationException(); }
        public S bestSolution() { return null; }
    }

    @SuppressWarnings("unchecked")
    @Test
    void theGATakesInMigrantsInThePlacesOfDuplicatesAndOldIndividualsButNeverOfTheBest() {
        HybridGA<PECTOSolution> ga = PECTOHybridGA.builder(PROBLEM).populationSize(20).build();
        try (SearchProcess p = ga.start(PROBLEM, new Direct<>(PROBLEM, 7))) {
            for (int g = 0; g < 300; g++)
                p.step();
            List<Individual<PECTOSolution>> before = ((Emigration<PECTOSolution>) p).emigrants();
            Individual<PECTOSolution> best = before.stream().min(Individual.BY_COST).orElseThrow();
            int distinct = new HashSet<>(before.stream().map(Individual::getRepresentation).toList()).size();
            assertTrue(distinct < before.size(), "the reduced GA's population has duplicates by now");

            List<Individual<PECTOSolution>> migrants = offered(2, 3);
            migrants.add(before.get(0));                                       // already a member: left out
            ((Immigration<PECTOSolution>) p).immigrate(migrants);
            List<Individual<PECTOSolution>> after = ((Emigration<PECTOSolution>) p).emigrants();
            List<PECTOSolution> members = after.stream().map(Individual::getRepresentation).toList();
            assertEquals(before.size(), after.size());
            assertTrue(after.contains(best));
            assertTrue(members.contains(migrants.get(0).getRepresentation()));
            assertTrue(members.contains(migrants.get(1).getRepresentation()));
            int expected = distinct + Math.min(2, before.size() - distinct);  // duplicates went first
            assertEquals(expected, new HashSet<>(members).size());
            assertEquals(((Immigration<PECTOSolution>) p).residents(), members);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void theColonyOffersItsAntsAndBestAndTakesInMigrantsAsPheromone() {
        PECTOACO aco = PECTOACO.builder().antCount(6).localSearchSteps(10).build();
        try (SearchProcess p = aco.start(PROBLEM, new Direct<>(PROBLEM, 2))) {
            assertTrue(((Emigration<PECTOSolution>) p).emigrants().size() >= 1);   // the greedy solution
            for (int i = 0; i < 5; i++)
                p.step();
            List<Individual<PECTOSolution>> offered = ((Emigration<PECTOSolution>) p).emigrants();
            assertEquals(6 + 2, offered.size());                                   // the ants, the iteration's and the overall best
            for (Individual<PECTOSolution> i : offered)
                assertEquals(PROBLEM.cost(i.getRepresentation()), i.getCost());
            List<PECTOSolution> residents = ((Immigration<PECTOSolution>) p).residents();
            assertEquals(1, residents.size());
            assertEquals(offered.stream().mapToDouble(Individual::getCost).min().orElseThrow(), PROBLEM.cost(residents.get(0)));
            ((Immigration<PECTOSolution>) p).immigrate(offered(2, 4));
            p.step();
            assertFalse(p.finished());
        }
    }

    // a steppable algorithm without migration: random solutions
    private static final class RandomSearch implements SteppableMetaheuristic<PECTOSolution, PECTOProblem> {
        @Override
        public SearchProcess start(PECTOProblem problem, SearchContext<PECTOSolution> ctx) {
            RandomPECTOSG generator = new RandomPECTOSG(problem);
            return new SearchProcess() {
                public void step() {
                    ctx.evaluate(generator.generate(problem, ctx.rng()));
                    ctx.iterationCompleted();
                }

                public boolean finished() {
                    return ctx.shouldStop();
                }
            };
        }

        @Override
        public String getName() {
            return "random";
        }
    }
}
