package problems.pecto;

import base.MutationOperator;
import org.junit.jupiter.api.Test;
import problems.pecto.operators.ChangeHospital;
import problems.pecto.operators.GreedyPECTOSG;
import problems.pecto.operators.MoveTrip;
import problems.pecto.operators.RandomPECTOSG;
import problems.pecto.operators.RouteCrossover;
import problems.pecto.operators.SwapTrips;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import static org.junit.jupiter.api.Assertions.*;

class PECTOOperatorsTest {

    private static final PECTOProblem PROBLEM = PECTOInstances.random(12, 150, 6, 30, 3);

    @Test
    void routeEditsLeaveTheRouteUnchanged() {
        PECTOProblem problem = PECTOInstances.random(4, 8, 2, 4, 7);    // patients 4-11, hospitals 12-13
        Route route = problem.route(0, 7, 13, 10, 12);
        assertArrayEquals(new int[]{10, 12}, route.withoutTrip(0).stops());
        assertArrayEquals(new int[]{7, 13, 10, 12, 4, 13}, route.withTrip(2, 4, 13).stops());
        assertArrayEquals(new int[]{4, 13, 7, 13, 10, 12}, route.withTrip(0, 4, 13).stops());
        assertArrayEquals(new int[]{7, 12, 10, 12}, route.withHospital(0, 12).stops());
        assertArrayEquals(new int[]{7, 13, 10, 12}, route.stops());
        assertThrows(IndexOutOfBoundsException.class, () -> route.withoutTrip(2));
        assertThrows(IndexOutOfBoundsException.class, () -> route.withTrip(3, 4, 13));
        assertThrows(IllegalArgumentException.class, () -> route.withTrip(0, 13, 4));
        assertThrows(IllegalArgumentException.class, () -> route.withHospital(0, 5));
    }

    @Test
    void randomSolutionsAreFeasible() {
        RNG rng = new MersenneTwisterRNG(1);
        PECTOProblem tight = PECTOInstances.random(5, 60, 3, 20, 4);   // every bed is needed
        PECTOProblem closed = PECTOProblem.builder().ambulance(0, 0).patient(1, 1, Urgency.HEAVY)
                .hospital(2, 2, 0).hospital(3, 3, 1).build();         // one hospital has no bed
        for (PECTOProblem problem : new PECTOProblem[]{PROBLEM, tight, closed})
            for (int k = 0; k < 20; k++)
                assertTrue(problem.isFeasible(new RandomPECTOSG(problem).generate(problem, rng)), problem.toString());
    }

    // Long walks: every neighbor is feasible, and its incremental cost equals that of a fresh copy
    @Test
    void mutationsKeepSolutionsFeasible() {
        RNG rng = new MersenneTwisterRNG(2);
        MoveTrip move = new MoveTrip();
        ChangeHospital change = new ChangeHospital();
        PECTOSolution solution = new RandomPECTOSG(PROBLEM).generate(PROBLEM, rng);
        PROBLEM.cost(solution);
        for (int step = 0; step < 5000; step++) {
            MutationOperator<PECTOSolution> op = rng.randInt(2) == 0 ? move : change;
            PECTOSolution next = op.apply(solution, rng);
            assertTrue(PROBLEM.isFeasible(next), op + " at step " + step);
            assertEquals(PROBLEM.cost(PROBLEM.decode(next.toSequence())), PROBLEM.cost(next), op + " at step " + step);
            if (op == move)
                for (int k = 0; k < PROBLEM.hospitalCount(); k++)
                    assertEquals(solution.load(PROBLEM.hospitalId(k)), next.load(PROBLEM.hospitalId(k)));
            solution = next;
        }
    }

    @Test
    void changeHospitalUsesFreeBedsOnly() {
        // two patients, one bed in each hospital: no other hospital has room
        PECTOProblem full = PECTOProblem.builder().ambulance(0, 0).patient(1, 1, Urgency.LIGHT).patient(2, 2, Urgency.LIGHT)
                .hospital(5, 5, 1).hospital(6, 6, 1).build();
        PECTOSolution solution = full.solution(new int[][]{{1, 3, 2, 4}});
        assertSame(solution, new ChangeHospital().apply(solution, new MersenneTwisterRNG(3)));
    }

    @Test
    void crossoverGivesFeasibleChildrenFromBothParents() {
        RNG rng = new MersenneTwisterRNG(4);
        RouteCrossover crossover = new RouteCrossover();
        RandomPECTOSG generator = new RandomPECTOSG(PROBLEM);
        for (int trial = 0; trial < 200; trial++) {
            PECTOSolution a = generator.generate(PROBLEM, rng), b = generator.generate(PROBLEM, rng);
            PECTOSolution child = crossover.apply(a, b, rng).get(0);
            assertTrue(PROBLEM.isFeasible(child));
            assertEquals(PROBLEM.cost(PROBLEM.decode(child.toSequence())), PROBLEM.cost(child));
        }
        // identical parents give the same solution, sharing its routes
        PECTOSolution parent = generator.generate(PROBLEM, rng);
        PECTOSolution child = crossover.apply(parent, parent, rng).get(0);
        assertEquals(parent, child);
        for (int a = 0; a < PROBLEM.ambulanceCount(); a++)
            assertSame(parent.route(a), child.route(a));
    }

    @Test
    void greedySolutionsAreFeasibleAndDeterministic() {
        PECTOProblem tight = PECTOInstances.random(5, 60, 3, 20, 4);   // every bed is needed
        for (PECTOProblem problem : new PECTOProblem[]{PROBLEM, tight}) {
            PECTOSolution a = new GreedyPECTOSG(problem).generate(problem, new MersenneTwisterRNG(1));
            PECTOSolution b = new GreedyPECTOSG(problem).generate(problem, new MersenneTwisterRNG(2));
            assertTrue(problem.isFeasible(a), problem.toString());
            assertEquals(a, b);
        }
        // greedy is far better than random on average
        double random = PROBLEM.cost(new RandomPECTOSG(PROBLEM).generate(PROBLEM, new MersenneTwisterRNG(3)));
        assertTrue(PROBLEM.cost(new GreedyPECTOSG(PROBLEM).generate(PROBLEM, null)) < random);
    }

    @Test
    void completeKeepsExistingTripsAndRepairFixesViolations() {
        PECTOProblem problem = PECTOProblem.builder().ambulance(0, 0).ambulance(9, 9)
                .patient(1, 1, Urgency.LIGHT).patient(2, 2, Urgency.CRITICAL).patient(3, 3, Urgency.HEAVY)
                .hospital(5, 5, 1).hospital(6, 6, 2).build();          // patients 2-4, hospitals 5-6
        PECTOSolution partial = problem.solution(new int[][]{{3, 6}, {}});
        PECTOSolution complete = GreedyPECTOSG.complete(partial);
        assertTrue(problem.isFeasible(complete));
        assertEquals(3, complete.route(0).patient(0));
        assertEquals(6, complete.route(0).hospital(0));

        // patient 2 twice, hospital 5 over capacity
        PECTOSolution broken = problem.solution(new int[][]{{2, 5, 3, 5}, {2, 6}});
        assertFalse(problem.isFeasible(broken));
        PECTOSolution repaired = GreedyPECTOSG.repair(broken);
        assertTrue(problem.isFeasible(repaired));
        assertEquals(2, repaired.route(0).patient(0));            // the first trips are kept
        assertSame(GreedyPECTOSG.repair(repaired), repaired);     // nothing to do for a feasible solution
    }

    @Test
    void swapTripsKeepsLoadsAndFeasibility() {
        RNG rng = new MersenneTwisterRNG(5);
        SwapTrips swap = new SwapTrips();
        PECTOSolution solution = new RandomPECTOSG(PROBLEM).generate(PROBLEM, rng);
        for (int step = 0; step < 3000; step++) {
            PECTOSolution before = PROBLEM.decode(solution.toSequence());
            PECTOSolution next = swap.apply(solution, rng);
            assertEquals(before, solution, "the input changed");
            assertNotEquals(solution, next);
            assertTrue(PROBLEM.isFeasible(next));
            assertEquals(PROBLEM.cost(PROBLEM.decode(next.toSequence())), PROBLEM.cost(next));
            for (int k = 0; k < PROBLEM.hospitalCount(); k++)
                assertEquals(solution.load(PROBLEM.hospitalId(k)), next.load(PROBLEM.hospitalId(k)));
            solution = next;
        }
        PECTOSolution one = PROBLEM.solution(new int[PROBLEM.ambulanceCount()][0]).withRoutes(PROBLEM.route(0, 12, 162));
        assertSame(one, swap.apply(one, rng));                     // a single trip cannot be swapped
    }
}
