package problems.pecto;

import base.RandomPermutationSG;
import metaheuristic.aco.ACO;
import metaheuristic.run.RunResult;
import metaheuristic.run.Runner;
import metaheuristic.termination.EvaluationBasedTC;
import org.junit.jupiter.api.Test;
import representation.IntegerPermutation;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import static org.junit.jupiter.api.Assertions.*;

class PECTOGraphTest {

    // ambulances 0-2, patients 3-8, hospitals 9-10
    private static final PECTOProblem PROBLEM = PECTOInstances.random(3, 6, 2, 3, 5);
    private static final PECTOGraph GRAPH = new PECTOGraph(PROBLEM);

    // The tour is a cycle cut at the ambulances: patients 7 and 8, before the first ambulance, belong to the last one
    @Test
    void cutsTheCycleAtTheAmbulances() {
        assertEquals(9, GRAPH.getNodeCount());
        PECTOSolution solution = GRAPH.decode(new IntegerPermutation(new int[]{7, 1, 3, 4, 0, 2, 5, 6, 8}));
        assertPatients(solution.route(1), 3, 4);
        assertPatients(solution.route(0));
        assertPatients(solution.route(2), 5, 6, 8, 7);
        assertTrue(PROBLEM.isFeasible(solution));
    }

    private static void assertPatients(Route route, int... patients) {
        assertEquals(patients.length, route.tripCount(), route.toString());
        for (int t = 0; t < patients.length; t++)
            assertEquals(patients[t], route.patient(t), route.toString());
    }

    @Test
    void everyTourDecodesToAFeasibleSolution() {
        RNG rng = new MersenneTwisterRNG(1);
        PECTOProblem tight = PECTOInstances.random(8, 90, 3, 30, 2);   // every bed is needed
        for (PECTOProblem problem : new PECTOProblem[]{PROBLEM, tight}) {
            PECTOGraph graph = new PECTOGraph(problem);
            RandomPermutationSG tours = new RandomPermutationSG(graph.getNodeCount());
            for (int k = 0; k < 200; k++) {
                IntegerPermutation tour = tours.generate(graph, rng);
                PECTOSolution solution = graph.decode(tour);
                assertTrue(graph.isFeasible(tour));
                assertTrue(problem.isFeasible(solution));
                assertEquals(problem.cost(solution), graph.cost(tour));
            }
        }
        assertFalse(GRAPH.isFeasible(new IntegerPermutation(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 7})));
    }

    @Test
    void distancesEstimateTheCostOfAStep() {
        int ambulance = 0, patient = 3, other = 4;
        assertEquals(PROBLEM.urgencyWeight(patient) * PROBLEM.distance(ambulance, patient), GRAPH.getDistance(ambulance, patient), 1e-12);
        int nearest = PROBLEM.distance(patient, 9) <= PROBLEM.distance(patient, 10) ? 9 : 10;
        assertEquals(PROBLEM.urgencyWeight(patient) * PROBLEM.distance(patient, nearest)
                        + PROBLEM.urgencyWeight(other) * PROBLEM.distance(nearest, other),
                GRAPH.getDistance(patient, other), 1e-12);
        assertTrue(GRAPH.getDistance(patient, ambulance) > PROBLEM.urgencyWeight(patient) * PROBLEM.distance(patient, nearest));
    }

    @Test
    void acoFindsFeasibleSolutions() {
        PECTOProblem problem = PECTOInstances.random(5, 40, 3, 20, 8);
        PECTOGraph graph = new PECTOGraph(problem);
        RunResult<IntegerPermutation> result = Runner.of(ACO.builder().antCount(10).build())
                .seed(3).stopWhen(new EvaluationBasedTC(500)).run(graph);
        PECTOSolution best = graph.decode(result.bestSolution());
        assertTrue(problem.isFeasible(best));
        assertEquals(result.bestCost(), problem.cost(best));
    }
}
