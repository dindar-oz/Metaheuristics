package problems.pecto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PECTOProblemTest {

    // The participants of Fig. 2: ambulances 0-3, patients 4-11, hospitals 12-13
    private static PECTOProblem figure2(int capacity) {
        return PECTOInstances.random(4, 8, 2, capacity, 7);
    }

    private static final int[] PARENT_1 = {0, 7, 13, 10, 12, 1, 5, 13, 4, 12, 2, 8, 13, 6, 12, 3, 9, 12, 11, 13};
    private static final int[] COPY_PHASE = {0, 7, 13, 10, 12, 1, 4, 13, 8, 13, 2, 6, 12, 3, 9, 12};

    @Test
    void numbersParticipantsByType() {
        PECTOProblem problem = figure2(4);
        assertEquals(14, problem.participantCount());
        assertEquals(ParticipantType.AMBULANCE, problem.typeOf(3));
        assertEquals(ParticipantType.PATIENT, problem.typeOf(4));
        assertEquals(ParticipantType.PATIENT, problem.typeOf(11));
        assertEquals(ParticipantType.HOSPITAL, problem.typeOf(12));
        assertThrows(IllegalArgumentException.class, () -> problem.typeOf(14));
        assertEquals(4, problem.patientId(0));
        assertEquals(13, problem.hospitalId(1));
        assertEquals(7, problem.index(11));
        assertEquals(1, problem.index(13));
    }

    // Hand-computed: legs to and from a patient cost distance x w(U_p) (Eq. 8), routes and solutions add up (Eqs. 9-10)
    @Test
    void costIsTheUrgencyWeightedDistance() {
        PECTOProblem problem = PECTOProblem.builder()
                .hospital(3, 0, 2)                      // ID 4, added first but numbered after the patients
                .ambulance(0, 0)                        // ID 0
                .ambulance(10, 0)                       // ID 1
                .patient(3, 4, Urgency.CRITICAL)        // ID 2
                .patient(6, 8, Urgency.STABLE)          // ID 3
                .build();
        PECTOSolution solution = problem.solution(new int[][]{{2, 4, 3, 4}, {}});
        double route0 = 0.2 * (5 + 4) + 1.0 * (Math.sqrt(73) + Math.sqrt(73));
        assertEquals(route0, problem.routeCost(solution.route(0)), 1e-12);
        assertEquals(0, problem.routeCost(solution.route(1)));
        assertEquals(route0, problem.cost(solution), 1e-12);
        assertTrue(problem.isFeasible(solution));

        PECTOSolution other = problem.solution(new int[][]{{2, 4}, {3, 4}});
        assertEquals(0.2 * (5 + 4) + 1.0 * (Math.sqrt(80) + Math.sqrt(73)), problem.cost(other), 1e-12);
    }

    @Test
    void distancesAreSymmetric() {
        PECTOProblem problem = PECTOInstances.random(5, 30, 4, 10, 3);
        for (int i = 0; i < problem.participantCount(); i++)
            for (int j = 0; j < problem.participantCount(); j++)
                assertEquals(problem.distance(i, j), problem.distance(j, i));
    }

    @Test
    void distanceIsTheShareOfDifferingChoices() {
        PECTOProblem problem = figure2(4);
        PECTOSolution a = problem.decode(PARENT_1);
        assertEquals(0, a.distanceTo(a));
        // patient 11 goes to hospital 12 instead of 13: 1 of the 2 x 8 choices differs
        int[] otherHospital = PARENT_1.clone();
        otherHospital[otherHospital.length - 1] = 12;
        PECTOSolution b = problem.decode(otherHospital);
        assertEquals(1.0 / 16, a.distanceTo(b));
        assertEquals(a.distanceTo(b), b.distanceTo(a));
        // ambulance 3 serves 11 before 9: the stops before 9 and 11 differ
        int[] swapped = {0, 7, 13, 10, 12, 1, 5, 13, 4, 12, 2, 8, 13, 6, 12, 3, 11, 13, 9, 12};
        assertEquals(2.0 / 16, a.distanceTo(problem.decode(swapped)));
    }

    @Test
    void decodesAndEncodesThePaperSequence() {
        PECTOProblem problem = figure2(4);
        PECTOSolution parent = problem.decode(PARENT_1);
        assertArrayEquals(PARENT_1, parent.toSequence());
        assertEquals(2, parent.route(1).tripCount());
        assertEquals(5, parent.route(1).patient(0));
        assertEquals(12, parent.route(1).hospital(1));
        assertEquals(12, parent.route(1).end());

        // the routes may come in any order; empty routes are a lone ambulance ID
        PECTOSolution shuffled = problem.decode(new int[]{3, 9, 12, 11, 13, 1, 0, 7, 13, 2, 5, 13});
        assertArrayEquals(new int[]{0, 7, 13, 1, 2, 5, 13, 3, 9, 12, 11, 13}, shuffled.toSequence());
        assertTrue(shuffled.route(1).isEmpty());
        assertEquals(1, shuffled.route(1).end());
    }

    @Test
    void rejectsSequencesThatBreakTheRouteRules() {
        PECTOProblem problem = figure2(4);
        int[][] malformed = {
                {},
                {7, 13, 0, 1, 2, 3},                  // does not start with an ambulance
                {0, 7, 7, 1, 2, 3},                   // a patient where a hospital is expected
                {0, 13, 7, 1, 2, 3},                  // a hospital where a patient is expected
                {0, 7, 1, 2, 3},                      // a patient without a hospital
                {0, 1, 2, 3, 0},                      // an ambulance with two routes
                {0, 1, 2},                            // an ambulance without a route
                {0, 1, 2, 3, 14},                     // not a participant
        };
        for (int[] sequence : malformed)
            assertThrows(IllegalArgumentException.class, () -> problem.decode(sequence), Arrays.toString(sequence));
    }

    @Test
    void feasibilityFollowsEquations12And13() {
        PECTOProblem roomy = figure2(4);
        assertTrue(roomy.isFeasible(roomy.decode(PARENT_1)));
        assertEquals(4, roomy.decode(PARENT_1).load(12));
        assertEquals(0, roomy.room(roomy.decode(PARENT_1), 13));

        // the copy phase of Fig. 2 has lost patients 5 and 11
        assertFalse(roomy.isFeasible(roomy.decode(COPY_PHASE)));
        // patient 7 transported twice
        assertFalse(roomy.isFeasible(roomy.decode(new int[]{0, 7, 13, 10, 12, 1, 7, 13, 4, 12, 2, 8, 13, 6, 12, 3, 9, 12, 11, 13})));
        assertFalse(roomy.isFeasible(roomy.emptySolution()));

        // five patients delivered to hospital 13
        int[] five = {0, 7, 13, 10, 13, 1, 5, 13, 4, 12, 2, 8, 13, 6, 12, 3, 9, 12, 11, 13};
        assertFalse(roomy.isFeasible(roomy.decode(five)));
        PECTOProblem larger = figure2(5);
        assertTrue(larger.isFeasible(larger.decode(five)));
        assertFalse(larger.isFeasible(roomy.decode(five)));   // a solution of another problem

        assertThrows(IllegalArgumentException.class, () -> PECTOInstances.random(4, 8, 2, 3, 7));
    }

    @Test
    void solutionsAreImmutableValues() {
        PECTOProblem problem = figure2(4);
        PECTOSolution parent = problem.decode(PARENT_1);
        Route moved = problem.route(1, 5, 13);
        PECTOSolution child = parent.withRoutes(moved);
        assertArrayEquals(PARENT_1, parent.toSequence());
        assertSame(parent.route(0), child.route(0));
        assertEquals(problem.route(1, 5, 13), child.route(1));
        assertEquals(child, problem.decode(child.toSequence()));
        assertEquals(child.hashCode(), problem.decode(child.toSequence()).hashCode());
        assertNotEquals(parent, child);

        int[] stops = {5, 13};
        Route route = problem.route(1, stops);
        stops[0] = 4;
        assertEquals(5, route.patient(0));

        assertThrows(IllegalArgumentException.class, () -> parent.withRoutes(moved, moved));
        assertThrows(IllegalArgumentException.class, () -> parent.withRoutes(figure2(4).route(1, 5, 13)));
        assertThrows(IllegalArgumentException.class, () -> figure2(4).cost(parent));
    }

    // Routes keep their cost; a solution sharing routes with evaluated ones must cost exactly what a fresh copy costs
    @Test
    void cachedEvaluationEqualsFreshEvaluation() {
        PECTOProblem problem = PECTOInstances.random(20, 400, 8, 60, 11);
        Random random = new Random(5);
        PECTOSolution current = randomFeasible(problem, random);
        for (int step = 0; step < 2000; step++) {
            // move a random trip to a random position of a random route
            int from = random.nextInt(problem.ambulanceCount());
            if (current.route(from).isEmpty())
                continue;
            int to = random.nextInt(problem.ambulanceCount());
            List<Integer> source = toList(current.route(from).stops());
            int trip = random.nextInt(source.size() / 2);
            int patient = source.remove(2 * trip), hospital = source.remove(2 * trip);
            List<Integer> target = from == to ? source : toList(current.route(to).stops());
            int position = 2 * random.nextInt(target.size() / 2 + 1);
            target.add(position, hospital);
            target.add(position, patient);
            PECTOSolution next = from == to
                    ? current.withRoutes(problem.route(from, toArray(target)))
                    : current.withRoutes(problem.route(from, toArray(source)), problem.route(to, toArray(target)));

            assertTrue(problem.isFeasible(next));
            assertEquals(problem.cost(problem.decode(next.toSequence())), problem.cost(next), "step " + step);
            current = next;
        }
    }

    @Test
    void generatesTheInstancesOfTable1() {
        int[] patients = {2430, 2733, 3037, 3341, 3645, 3948, 4252, 4556, 4860, 5163};
        for (int instance = 1; instance <= PECTOInstances.INSTANCE_COUNT; instance++) {
            assertEquals(patients[instance - 1], PECTOInstances.patientCount(instance));
            assertEquals(0.56 + 0.08 * instance, PECTOInstances.multiplier(instance), 1e-12);
        }
        assertThrows(IllegalArgumentException.class, () -> PECTOInstances.patientCount(11));

        PECTOProblem largest = PECTOInstances.paper(10, 1);
        assertEquals(200, largest.ambulanceCount());
        assertEquals(5163, largest.patientCount());
        assertEquals(64, largest.hospitalCount());
        assertEquals(64 * 81, largest.totalCapacity());
        for (int id = 0; id < largest.participantCount(); id++) {
            assertTrue(largest.x(id) >= 0 && largest.x(id) < 350 && largest.y(id) >= 0 && largest.y(id) < 350);
        }
        assertTrue(largest.isFeasible(randomFeasible(largest, new Random(2))));

        PECTOProblem again = PECTOInstances.paper(10, 1);
        for (int id = 0; id < largest.participantCount(); id++)
            assertEquals(largest.x(id), again.x(id));
        for (int k = 0; k < largest.patientCount(); k++)
            assertEquals(largest.urgency(largest.patientId(k)), again.urgency(again.patientId(k)));
    }

    // Every patient on a random ambulance, delivered to a random hospital that still has room
    static PECTOSolution randomFeasible(PECTOProblem problem, Random random) {
        List<Integer> patients = new ArrayList<>();
        for (int k = 0; k < problem.patientCount(); k++)
            patients.add(problem.patientId(k));
        Collections.shuffle(patients, random);
        List<List<Integer>> stops = new ArrayList<>();
        for (int a = 0; a < problem.ambulanceCount(); a++)
            stops.add(new ArrayList<>());
        int[] room = new int[problem.hospitalCount()];
        for (int k = 0; k < room.length; k++)
            room[k] = problem.capacity(problem.hospitalId(k));
        for (int patient : patients) {
            int h;
            do {
                h = random.nextInt(room.length);
            } while (room[h] == 0);
            room[h]--;
            List<Integer> route = stops.get(random.nextInt(stops.size()));
            route.add(patient);
            route.add(problem.hospitalId(h));
        }
        int[][] arrays = new int[stops.size()][];
        for (int a = 0; a < arrays.length; a++)
            arrays[a] = toArray(stops.get(a));
        return problem.solution(arrays);
    }

    private static List<Integer> toList(int[] values) {
        List<Integer> list = new ArrayList<>(values.length);
        for (int v : values)
            list.add(v);
        return list;
    }

    private static int[] toArray(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }
}
