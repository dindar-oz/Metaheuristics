package problems.pecto;

import org.junit.jupiter.api.Test;
import problems.pecto.operators.GreedyPECTOSG;
import problems.pecto.operators.OptimalHospitals;
import problems.pecto.operators.RandomPECTOSG;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import static org.junit.jupiter.api.Assertions.*;

class OptimalHospitalsTest {

    // Every assignment of hospitals to the trips is tried: 3^8 of them, most over capacity when beds are scarce
    @Test
    void findsTheBestHospitalsForTheRoutes() {
        RNG rng = new MersenneTwisterRNG(4);
        for (int seed = 0; seed < 20; seed++) {
            PECTOProblem problem = PECTOInstances.random(3, 8, 3, 3 + seed % 2, seed);   // 9 or 12 beds
            PECTOSolution solution = new RandomPECTOSG(problem).generate(problem, rng);
            PECTOSolution optimal = OptimalHospitals.of(solution);
            assertTrue(problem.isFeasible(optimal));
            assertSameRoutes(solution, optimal);
            assertEquals(bruteForce(solution), problem.cost(optimal), 1e-9);
        }
    }

    private static double bruteForce(PECTOSolution solution) {
        PECTOProblem problem = solution.problem();
        int trips = solution.tripCount(), hospitals = problem.hospitalCount();
        int[][] stops = new int[solution.routeCount()][];
        for (int a = 0; a < stops.length; a++)
            stops[a] = solution.route(a).stops();
        double best = Double.POSITIVE_INFINITY;
        int combinations = (int) Math.pow(hospitals, trips);
        for (int code = 0; code < combinations; code++) {
            int rest = code;
            for (int[] route : stops)
                for (int k = 1; k < route.length; k += 2) {
                    route[k] = problem.hospitalId(rest % hospitals);
                    rest /= hospitals;
                }
            PECTOSolution candidate = problem.solution(stops);
            if (problem.isFeasible(candidate))
                best = Math.min(best, problem.cost(candidate));
        }
        return best;
    }

    private static void assertSameRoutes(PECTOSolution expected, PECTOSolution actual) {
        for (int a = 0; a < expected.routeCount(); a++) {
            Route e = expected.route(a), r = actual.route(a);
            assertEquals(e.tripCount(), r.tripCount());
            for (int t = 0; t < e.tripCount(); t++)
                assertEquals(e.patient(t), r.patient(t));
        }
    }

    @Test
    void neverWorsensAndKeepsOptimalSolutions() {
        PECTOProblem problem = PECTOInstances.random(8, 90, 3, 30, 2);   // every bed is needed
        PECTOSolution greedy = GreedyPECTOSG.complete(problem.emptySolution());
        PECTOSolution optimal = OptimalHospitals.of(greedy);
        assertTrue(problem.isFeasible(optimal));
        assertTrue(problem.cost(optimal) <= problem.cost(greedy));
        assertSame(optimal, OptimalHospitals.of(optimal));
    }

    @Test
    void refusesSolutionsOverCapacity() {
        PECTOProblem problem = PECTOInstances.random(1, 3, 2, 2, 1);    // patients 1-3, hospitals 4-5 of 2 beds
        PECTOSolution crowded = problem.solution(new int[][]{{1, 4, 2, 4, 3, 4}});
        assertThrows(IllegalArgumentException.class, () -> OptimalHospitals.of(crowded));
    }
}
