package lab.model;

import metaheuristic.run.RunResult;
import metaheuristic.run.SearchListener;
import metaheuristic.termination.EvaluationBasedTC;
import org.junit.jupiter.api.Test;

import base.RandomPermutationSG;
import problems.tsp.NearestNeighborTSPSG;
import problems.tsp.TSPProblem;
import representation.IntegerPermutation;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every algorithm the lab offers must run on its problem with the default parameters and produce a solution that
 * its visualizer can show.
 */
class CatalogTest {

    @Test
    void everyAlgorithmRunsAndCanBeVisualized() {
        List<String> failures = new ArrayList<>();
        for (LabProblem problem : Catalog.problems()) {
            LabProblem.Session session = problem.newSession(ParamValues.defaults(problem.params()));
            for (String algorithm : problem.algorithmNames()) {
                String name = problem.name() + " / " + algorithm;
                try {
                    LabProblem.StartedRun run = session.start(algorithm, ParamValues.defaults(problem.algorithmParams(algorithm)),
                            1, new EvaluationBasedTC(300), new SearchListener() {
                            }, Runnable::run);           // runs synchronously
                    RunResult<?> result = run.handle().result().join();
                    assertTrue(result.evaluations() > 0 && result.evaluations() <= 300, name + " evaluations");
                    assertTrue(Double.isFinite(result.bestCost()), name + " cost");
                    assertTrue(run.hasSolution(), name + " has no solution");
                    assertNotNull(run.viewBestSolution(), name + " view");
                } catch (RuntimeException | AssertionError e) {
                    failures.add(name + ": " + e);
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    // every alternative of every choice parameter (e.g. the initial solutions or the mutation operators) must run
    @Test
    void everyChoiceRuns() {
        List<String> failures = new ArrayList<>();
        for (LabProblem problem : Catalog.problems()) {
            LabProblem.Session session = problem.newSession(ParamValues.defaults(problem.params()));
            for (String algorithm : problem.algorithmNames())
                for (ParamSpec spec : problem.algorithmParams(algorithm)) {
                    if (spec.kind() != ParamSpec.Kind.CHOICE)
                        continue;
                    for (String choice : spec.choices()) {
                        String name = problem.name() + " / " + algorithm + " / " + spec.name() + " = " + choice;
                        try {
                            ParamValues values = ParamValues.defaults(problem.algorithmParams(algorithm));
                            values.set(spec.name(), choice);
                            RunResult<?> result = session.start(algorithm, values, 1, new EvaluationBasedTC(300),
                                    new SearchListener() {
                                    }, Runnable::run).handle().result().join();
                            assertTrue(Double.isFinite(result.bestCost()), name + " cost");
                        } catch (RuntimeException | AssertionError e) {
                            failures.add(name + ": " + e);
                        }
                    }
                }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    // the defaults are the ingredients the lab used before the choices existed, so earlier runs keep their meaning
    @Test
    void pectoOffersGreedyStartsAndSwapTrips() {
        LabProblem pecto = problem("PECTO");
        for (String algorithm : Arrays.asList("EA", "SA", "Local Search")) {
            ParamSpec initial = param(pecto, algorithm, "initial solutions");
            assertEquals(Arrays.asList("random", "greedy"), initial.choices(), algorithm);
            assertEquals("random", initial.defaultValue(), algorithm);
            ParamSpec moves = param(pecto, algorithm, "mutation operators");
            assertEquals("MoveTrip + ChangeHospital", moves.defaultValue(), algorithm);
            assertTrue(moves.choices().contains("MoveTrip + ChangeHospital + SwapTrips"), algorithm);
        }
        // a problem with a single alternative still shows it, so that the lab always tells what a run starts from
        assertEquals(Collections.singletonList("random"), param(problem("Sudoku"), "SA", "initial solutions").choices());
        assertEquals(Arrays.asList("random", "nearest neighbor"), param(problem("TSP"), "EA", "initial solutions").choices());
    }

    // nearest-neighbor tours visit every city once, start at a random city, and are far shorter than random tours
    @Test
    void nearestNeighborToursAreValidAndShort() {
        TSPProblem tsp = TSPProblem.randomEuclidean(60, 3);
        RNG rng = new MersenneTwisterRNG(5);
        Set<Integer> starts = new HashSet<>();
        double nn = 0, random = 0;
        for (int i = 0; i < 20; i++) {
            IntegerPermutation tour = new NearestNeighborTSPSG(tsp).generate(tsp, rng);
            assertTrue(tsp.isFeasible(tour));
            starts.add(tour.get(0));
            nn += tsp.cost(tour);
            random += tsp.cost(new RandomPermutationSG(60).generate(tsp, rng));
        }
        assertTrue(starts.size() > 1, "the start city is random");
        assertTrue(nn < 0.5 * random, "nearest neighbor " + nn / 20 + " vs random " + random / 20);
    }

    // the generator is part of the algorithm's description, which is recorded with every run
    @Test
    void runsRecordTheirInitialSolutions() {
        LabProblem pecto = problem("PECTO");
        ParamValues problemValues = ParamValues.defaults(pecto.params());
        problemValues.set("instance", "custom");
        LabProblem.Session session = pecto.newSession(problemValues);
        for (String generator : Arrays.asList("random", "greedy")) {
            ParamValues values = ParamValues.defaults(pecto.algorithmParams("SA"));
            values.set("initial solutions", generator);
            RunResult<?> result = session.start("SA", values, 1, new EvaluationBasedTC(50), new SearchListener() {
            }, Runnable::run).handle().result().join();
            String expected = generator.equals("random") ? "initial=RandomPECTOSG" : "initial=GreedyPECTOSG";
            assertTrue(result.info().algorithm().contains(expected), result.info().algorithm());
        }
    }

    private static LabProblem problem(String part) {
        return Catalog.problems().stream().filter(p -> p.name().contains(part)).findFirst().orElseThrow(AssertionError::new);
    }

    private static ParamSpec param(LabProblem problem, String algorithm, String name) {
        return problem.algorithmParams(algorithm).stream().filter(s -> s.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError(algorithm + " has no parameter " + name));
    }

    @Test
    void windFarmOffersBothEncodings() {
        LabProblem wflop = Catalog.problems().stream().filter(p -> p.name().contains("WFLOP")).findFirst().orElseThrow(AssertionError::new);
        assertTrue(wflop.algorithmNames().containsAll(Arrays.asList("DE", "PSO", "EA", "SA", "Local Search", "DQN")));
    }
}
