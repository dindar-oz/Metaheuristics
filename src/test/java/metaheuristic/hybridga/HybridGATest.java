package metaheuristic.hybridga;

import base.InitialSolutionGenerator;
import metaheuristic.run.RunResult;
import metaheuristic.run.Runner;
import metaheuristic.termination.EvaluationBasedTC;
import org.junit.jupiter.api.Test;
import problems.pecto.PECTOInstances;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import metaheuristic.sa.GeometricCooling;
import problems.pecto.operators.GreedyPECTOSG;
import problems.pecto.operators.MoveTrip;
import problems.pecto.operators.RandomPECTOSG;
import problems.pecto.operators.RouteCrossover;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HybridGATest {

    private static final PECTOProblem PROBLEM = PECTOInstances.random(12, 150, 6, 30, 3);
    // 90 patients, 3 hospitals of 30 beds: every bed is needed
    private static final PECTOProblem FULL = PECTOInstances.random(8, 90, 3, 30, 2);

    private static RunResult<PECTOSolution> run(HybridGA<PECTOSolution> ga, PECTOProblem problem, long seed, int evaluations) {
        return Runner.of(ga).seed(seed).stopWhen(new EvaluationBasedTC(evaluations)).run(problem);
    }

    private static PECTOSolution random(PECTOProblem problem, RNG rng) {
        return new RandomPECTOSG(problem).generate(problem, rng);
    }

    @Test
    void generatorsBuildFeasibleSolutions() {
        RNG rng = new MersenneTwisterRNG(1);
        for (PECTOProblem problem : new PECTOProblem[]{PROBLEM, FULL})
            for (InitialSolutionGenerator<PECTOSolution> g : new InitialSolutionGenerator[]{
                    new PartialGreedyPECTOSG(problem), new PartialGreedyPECTOSG(problem, 0),
                    new RandomizedGreedyPECTOSG(problem), new RandomizedGreedyPECTOSG(problem, 100)})
                for (int i = 0; i < 20; i++)
                    assertTrue(problem.isFeasible(g.generate(problem, rng)), g.toString());
    }

    @Test
    void randomizedGreedyWithOneCandidateIsTheGreedySolution() {
        PECTOSolution greedy = new GreedyPECTOSG(PROBLEM).generate(PROBLEM, new MersenneTwisterRNG(1));
        PECTOSolution one = new RandomizedGreedyPECTOSG(PROBLEM, 1).generate(PROBLEM, new MersenneTwisterRNG(2));
        assertArrayEquals(greedy.toSequence(), one.toSequence());
    }

    @Test
    void partialGreedyKeepingEveryRouteIsTheGreedySolution() {
        PECTOSolution greedy = new GreedyPECTOSG(PROBLEM).generate(PROBLEM, new MersenneTwisterRNG(1));
        PECTOSolution kept = new PartialGreedyPECTOSG(PROBLEM, 1).generate(PROBLEM, new MersenneTwisterRNG(2));
        assertArrayEquals(greedy.toSequence(), kept.toSequence());
    }

    @Test
    void crossoverChildrenAreFeasible() {
        RNG rng = new MersenneTwisterRNG(4);
        for (PECTOProblem problem : new PECTOProblem[]{PROBLEM, FULL})
            for (double p : new double[]{0, 0.75, 1})
                for (boolean bestInsertion : new boolean[]{true, false})
                    for (int i = 0; i < 30; i++) {
                        PECTOSolution child = new AlternatingRouteCrossover(p, bestInsertion)
                                .apply(random(problem, rng), random(problem, rng), rng).get(0);
                        assertTrue(problem.isFeasible(child));
                    }
    }

    @Test
    void cloneCrossoverReturnsOneOfTheParents() {
        RNG rng = new MersenneTwisterRNG(14);
        PECTOSolution first = random(PROBLEM, rng), second = random(PROBLEM, rng);
        for (int i = 0; i < 20; i++) {
            PECTOSolution child = new CloneCrossover<PECTOSolution>().apply(first, second, rng).get(0);
            assertTrue(child.equals(first) || child.equals(second));
        }
    }

    @Test
    void everyMechanismCanBeSwitchedOff() {
        List<HybridGA.Builder<PECTOSolution>> variants = List.of(
                PECTOHybridGA.researchBuilder(FULL).clearSources().source(new RandomPECTOSG(FULL), 1),
                PECTOHybridGA.researchBuilder(FULL).crossover(new CloneCrossover<>()),
                PECTOHybridGA.researchBuilder(FULL).crossover(new RouteCrossover()),
                PECTOHybridGA.researchBuilder(FULL).crossover(new AlternatingRouteCrossover(0.5, false)),
                PECTOHybridGA.researchBuilder(FULL).mutation(new MoveTrip()),
                PECTOHybridGA.researchBuilder(FULL).mutationRate(0).mutationRateAdaptation(1, 0, 0),
                PECTOHybridGA.researchBuilder(FULL).immigration(false),
                PECTOHybridGA.researchBuilder(FULL).perturbationOnStagnation(false),
                PECTOHybridGA.researchBuilder(FULL).immigration(false).perturbationOnStagnation(false).mutationRateAdaptation(1, 0.05, 0.75),
                PECTOHybridGA.researchBuilder(FULL).minVictimAge(0),
                PECTOHybridGA.researchBuilder(FULL).localSearchSteps(0).annealing(25, 0.1, new GeometricCooling(0.95), 0));
        for (HybridGA.Builder<PECTOSolution> b : variants) {
            HybridGA<PECTOSolution> ga = b.populationSize(10).stagnationPeriod(2).build();
            RunResult<PECTOSolution> result = run(ga, FULL, 3, 4000);
            assertTrue(FULL.isFeasible(result.bestSolution()), ga.describe());
        }
    }

    @Test
    void everyMechanismOffByDefaultCanBeSwitchedOn() {
        List<HybridGA.Builder<PECTOSolution>> variants = List.of(
                PECTOHybridGA.builder(FULL).parentSelectors(null, null, 0.75),
                PECTOHybridGA.builder(FULL).mutationRate(0.05).mutationRateAdaptation(1.1, 0.05, 0.75),
                PECTOHybridGA.builder(FULL).stagnationPeriod(2),
                PECTOHybridGA.builder(FULL).annealing(25, 0.1, new GeometricCooling(0.95), 1));
        for (HybridGA.Builder<PECTOSolution> b : variants) {
            HybridGA<PECTOSolution> ga = b.populationSize(10).build();
            RunResult<PECTOSolution> result = run(ga, FULL, 3, 4000);
            assertTrue(FULL.isFeasible(result.bestSolution()), ga.describe());
        }
    }

    @Test
    void defaultIsTheReducedGAOfTheExperiments() {
        // the 'reduced' configuration of pecto-hga-reduced-r2 and the R of pecto-hga-components-r3, as lab parameters
        // on the research GA: roulette 0, mutation none, stagnation period 1,000,000, SA inner loop 0
        HybridGA<PECTOSolution> reduced = PECTOHybridGA.researchBuilder(PROBLEM).parentSelectors(null, null, 0)
                .mutationRate(0).mutationRateAdaptation(1, 0, 0).stagnationPeriod(1000000)
                .annealing(25, 0.1, new GeometricCooling(0.95), 0).build();
        RunResult<PECTOSolution> a = run(PECTOHybridGA.builder(PROBLEM).build(), PROBLEM, 8, 8000);
        RunResult<PECTOSolution> b = run(reduced, PROBLEM, 8, 8000);
        assertEquals(b.bestCost(), a.bestCost());
        assertArrayEquals(b.bestSolution().toSequence(), a.bestSolution().toSequence());
        assertEquals(0, a.finalProgress().metrics().get(HybridGA.IMMIGRATIONS));
        assertEquals(0, a.finalProgress().metrics().get(HybridGA.MUTATION_RATE));
    }

    @Test
    void switchingOffImmigrationAndPerturbationLeavesOnlyTheRateGrowth() {
        // with neither migrants nor perturbation, a stagnation changes nothing but the mutation rate, so a run without
        // mutation is the same whatever the stagnation period
        HybridGA.Builder<PECTOSolution> quiet = PECTOHybridGA.researchBuilder(FULL).populationSize(10).immigration(false)
                .perturbationOnStagnation(false).mutationRate(0).mutationRateAdaptation(1.1, 0, 0);
        RunResult<PECTOSolution> often = run(quiet.stagnationPeriod(2).build(), FULL, 6, 3000);
        RunResult<PECTOSolution> never = run(quiet.stagnationPeriod(1000000).build(), FULL, 6, 3000);
        assertEquals(never.bestCost(), often.bestCost());
        assertTrue(often.finalProgress().metrics().get(HybridGA.IMMIGRATIONS) > 0);
    }

    @Test
    void crossoverAlwaysTakingTheCheaperRouteNeverTakesTheCostlierOne() {
        RNG rng = new MersenneTwisterRNG(5);
        PECTOSolution first = random(PROBLEM, rng), second = random(PROBLEM, rng);
        PECTOSolution child = new AlternatingRouteCrossover(1).apply(first, second, rng).get(0);
        for (int a = 0; a < PROBLEM.ambulanceCount(); a++) {
            boolean firstIsCheaper = PROBLEM.routeCost(first.route(a)) <= PROBLEM.routeCost(second.route(a));
            assertNotSame(firstIsCheaper ? second.route(a) : first.route(a), child.route(a));
        }
    }

    @Test
    void crossoverSharesTheRoutesItCopiesWithoutLosses() {
        // the parents differ in two routes only: every other route is copied as it is
        PECTOSolution first = random(PROBLEM, new MersenneTwisterRNG(11));
        PECTOSolution second = new CostliestRouteMove().apply(first, new MersenneTwisterRNG(12));
        PECTOSolution child = new AlternatingRouteCrossover().apply(first, second, new MersenneTwisterRNG(13)).get(0);
        int shared = 0;
        for (int a = 0; a < PROBLEM.ambulanceCount(); a++)
            if (child.route(a) == first.route(a))
                shared++;
        assertTrue(shared >= PROBLEM.ambulanceCount() - 3);
        assertTrue(PROBLEM.isFeasible(child));
    }

    @Test
    void crossoverOfASolutionWithItselfIsTheSolution() {
        PECTOSolution parent = random(PROBLEM, new MersenneTwisterRNG(6));
        assertEquals(parent, new AlternatingRouteCrossover().apply(parent, parent, new MersenneTwisterRNG(7)).get(0));
    }

    @Test
    void mutationsKeepSolutionsFeasibleAndLoadsUnchanged() {
        RNG rng = new MersenneTwisterRNG(8);
        for (PECTOProblem problem : new PECTOProblem[]{PROBLEM, FULL}) {
            PECTOSolution s = random(problem, rng);
            for (int i = 0; i < 300; i++) {
                PECTOSolution next = (i % 2 == 0 ? new SwapParticipants() : new CostliestRouteMove()).apply(s, rng);
                assertTrue(problem.isFeasible(next));
                for (int k = 0; k < problem.hospitalCount(); k++)
                    assertEquals(s.load(problem.hospitalId(k)), next.load(problem.hospitalId(k)));
                s = next;
            }
        }
    }

    @Test
    void costliestRouteMoveShiftsATripFromTheCostliestToTheCheapestRoute() {
        PECTOSolution s = random(PROBLEM, new MersenneTwisterRNG(9));
        int costliest = 0, cheapest = 0;
        for (int a = 1; a < s.routeCount(); a++) {
            if (PROBLEM.routeCost(s.route(a)) > PROBLEM.routeCost(s.route(costliest)))
                costliest = a;
            if (PROBLEM.routeCost(s.route(a)) < PROBLEM.routeCost(s.route(cheapest)))
                cheapest = a;
        }
        PECTOSolution next = new CostliestRouteMove().apply(s, new MersenneTwisterRNG(10));
        assertEquals(s.route(costliest).tripCount() - 1, next.route(costliest).tripCount());
        assertEquals(s.route(cheapest).tripCount() + 1, next.route(cheapest).tripCount());
    }

    @Test
    void parentCountFollowsTheResearchTable() {
        HybridGA<PECTOSolution> ga = PECTOHybridGA.builder(PROBLEM).build();
        assertNotNull(ga.describe());
        assertEquals(5, HybridGA.researchParentCount(40));
        assertEquals(7, HybridGA.researchParentCount(41));
        assertEquals(10, HybridGA.researchParentCount(160));
        assertEquals(11, HybridGA.researchParentCount(161));
    }

    @Test
    void improvesOnTheGreedySolution() {
        RunResult<PECTOSolution> result = run(PECTOHybridGA.builder(PROBLEM).build(), PROBLEM, 3, 20000);
        assertTrue(PROBLEM.isFeasible(result.bestSolution()));
        assertEquals(PROBLEM.cost(result.bestSolution()), result.bestCost());
        assertTrue(result.bestCost() < PROBLEM.cost(GreedyPECTOSG.complete(PROBLEM.emptySolution())));
        assertTrue(result.evaluations() <= 20000);
    }

    @Test
    void plainGARunsAndImproves() {
        HybridGA<PECTOSolution> ga = PECTOHybridGA.builder(PROBLEM).hybrid(false).populationSize(20).build();
        RunResult<PECTOSolution> result = run(ga, PROBLEM, 5, 5000);
        assertTrue(PROBLEM.isFeasible(result.bestSolution()));
        assertTrue(result.finalProgress().iterations() > 10);
    }

    @Test
    void stagnationBringsMigrantsAndRaisesTheMutationRate() {
        HybridGA<PECTOSolution> ga = PECTOHybridGA.researchBuilder(FULL).stagnationPeriod(2).populationSize(10).build();
        RunResult<PECTOSolution> result = run(ga, FULL, 2, 20000);
        assertTrue(FULL.isFeasible(result.bestSolution()));
        assertTrue(result.finalProgress().metrics().get(HybridGA.IMMIGRATIONS) > 0);
        assertTrue(result.finalProgress().metrics().get(HybridGA.MUTATION_RATE) > 0.05);
    }

    @Test
    void sameSeedSameRun() {
        for (HybridGA.Builder<PECTOSolution> builder : List.of(PECTOHybridGA.builder(PROBLEM),
                PECTOHybridGA.researchBuilder(PROBLEM).stagnationPeriod(5))) {
            HybridGA<PECTOSolution> ga = builder.build();
            RunResult<PECTOSolution> a = run(ga, PROBLEM, 7, 6000), b = run(ga, PROBLEM, 7, 6000);
            assertEquals(a.bestCost(), b.bestCost());
            assertArrayEquals(a.bestSolution().toSequence(), b.bestSolution().toSequence());
        }
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> PECTOHybridGA.builder(PROBLEM).clearSources().build());
        assertThrows(IllegalArgumentException.class, () -> PECTOHybridGA.builder(PROBLEM).crossoverRate(2).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOHybridGA.builder(PROBLEM).refinement().build());
        assertThrows(IllegalArgumentException.class,
                () -> PECTOHybridGA.builder(PROBLEM).mutationRateAdaptation(1.1, 0.8, 0.5).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOHybridGA.builder(PROBLEM).stagnationPeriod(-1).build());
    }
}
