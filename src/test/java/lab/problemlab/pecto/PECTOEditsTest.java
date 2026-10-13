package lab.problemlab.pecto;

import org.junit.jupiter.api.Test;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.ParticipantType;
import problems.pecto.Urgency;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Instance edits carry the solution over to the new IDs; trip edits change only what they say.
 */
class PECTOEditsTest {

    // ambulances 0-1, patients 2-5, hospitals 6-7
    private static final PECTOProblem PROBLEM = PECTOProblem.builder()
            .ambulance(0, 0).ambulance(100, 0)
            .patient(10, 10, Urgency.CRITICAL).patient(20, 20, Urgency.STABLE).patient(90, 10, Urgency.LIGHT).patient(80, 20, Urgency.HEAVY)
            .hospital(50, 50, 4).hospital(60, 60, 4)
            .build();
    private static final PECTOSolution SOLUTION = PROBLEM.solution(new int[][]{{2, 6, 3, 6}, {4, 7, 5, 7}});

    @Test
    void addingAnAmbulanceShiftsTheOtherIds() {
        PECTOInstanceEdit.Result r = PECTOInstanceEdit.add(PROBLEM, SOLUTION, ParticipantType.AMBULANCE, 5, 5, null, 0);
        assertEquals(2, r.created());
        assertEquals(3, r.newId(2));                                // patient 2 is now 3
        assertEquals(8, r.newId(7));
        assertArrayEquals(new int[]{3, 7, 4, 7}, r.solution().route(0).stops());
        assertTrue(r.solution().route(2).isEmpty());
        assertTrue(r.problem().isFeasible(r.solution()));
        assertEquals(PROBLEM.cost(SOLUTION), r.problem().cost(r.solution()), 1e-12);
    }

    @Test
    void addingAPatientLeavesItUntransported() {
        PECTOInstanceEdit.Result r = PECTOInstanceEdit.add(PROBLEM, SOLUTION, ParticipantType.PATIENT, 30, 30, Urgency.MODERATE, 0);
        assertEquals(6, r.created());
        assertEquals(Urgency.MODERATE, r.problem().urgency(6));
        assertFalse(r.problem().isFeasible(r.solution()));
        assertNull(PECTOTripEdit.find(r.solution(), 6));
    }

    @Test
    void removingAParticipantDropsItsTrips() {
        PECTOInstanceEdit.Result patient = PECTOInstanceEdit.remove(PROBLEM, SOLUTION, 3);
        assertEquals(-1, patient.newId(3));
        assertArrayEquals(new int[]{2, 5}, patient.solution().route(0).stops());   // hospital 6 is now 5
        assertTrue(patient.problem().isFeasible(patient.solution()));

        PECTOInstanceEdit.Result hospital = PECTOInstanceEdit.remove(PROBLEM, SOLUTION, 7);
        assertTrue(hospital.solution().route(1).isEmpty());
        assertEquals(2, new PECTOFacts(hospital.problem(), hospital.solution()).missing);

        PECTOInstanceEdit.Result ambulance = PECTOInstanceEdit.remove(PROBLEM, SOLUTION, 0);
        assertEquals(1, ambulance.problem().ambulanceCount());
        assertArrayEquals(new int[]{3, 6, 4, 6}, ambulance.solution().route(0).stops());
    }

    @Test
    void invalidInstancesAreRefused() {
        PECTOProblem one = PECTOProblem.builder().ambulance(0, 0).hospital(1, 1, 0).build();
        assertThrows(IllegalArgumentException.class, () -> PECTOInstanceEdit.remove(one, null, 1));    // last hospital
        assertThrows(IllegalArgumentException.class,                                                     // no free bed
                () -> PECTOInstanceEdit.add(one, null, ParticipantType.PATIENT, 0, 0, Urgency.LIGHT, 0));
        assertThrows(IllegalArgumentException.class, () -> PECTOInstanceEdit.setCapacity(PROBLEM, SOLUTION, 2, 4));  // not a hospital
    }

    @Test
    void propertyEditsKeepIdsAndChangeTheCost() {
        PECTOInstanceEdit.Result moved = PECTOInstanceEdit.move(PROBLEM, SOLUTION, 2, 11, 10);
        assertEquals(11, moved.problem().x(2));
        assertNotEquals(PROBLEM.cost(SOLUTION), moved.problem().cost(moved.solution()));
        PECTOInstanceEdit.Result urgent = PECTOInstanceEdit.setUrgency(PROBLEM, SOLUTION, 3, Urgency.CRITICAL);
        assertTrue(urgent.problem().cost(urgent.solution()) < PROBLEM.cost(SOLUTION));
        PECTOInstanceEdit.Result smaller = PECTOInstanceEdit.setCapacity(PROBLEM, SOLUTION, 6, 1);
        assertFalse(smaller.problem().isFeasible(smaller.solution()));
        assertEquals(1, new PECTOFacts(smaller.problem(), smaller.solution()).overfull);
    }

    @Test
    void tripEdits() {
        // move patient 3 to the end of ambulance 1's route; it keeps hospital 6
        PECTOSolution moved = PECTOTripEdit.moveTrip(SOLUTION, 3, 1, -1);
        assertArrayEquals(new int[]{2, 6}, moved.route(0).stops());
        assertArrayEquals(new int[]{4, 7, 5, 7, 3, 6}, moved.route(1).stops());
        // insert patient 5 before patient 2
        PECTOSolution inserted = PECTOTripEdit.insertBefore(SOLUTION, 5, 2);
        assertArrayEquals(new int[]{5, 7, 2, 6, 3, 6}, inserted.route(0).stops());
        // within a route: patient 3 before patient 2
        assertArrayEquals(new int[]{3, 6, 2, 6}, PECTOTripEdit.insertBefore(SOLUTION, 3, 2).route(0).stops());
        assertArrayEquals(new int[]{3, 6, 2, 6}, PECTOTripEdit.shift(SOLUTION, 2, 1).route(0).stops());
        assertSame(SOLUTION, PECTOTripEdit.shift(SOLUTION, 2, -1));
        assertArrayEquals(new int[]{3, 6, 2, 6}, PECTOTripEdit.reverseRoute(SOLUTION, 0).route(0).stops());
        assertArrayEquals(new int[]{2, 7, 3, 6}, PECTOTripEdit.deliver(SOLUTION, 2, 7).route(0).stops());
        assertTrue(PECTOTripEdit.clearRoute(SOLUTION, 1).route(1).isEmpty());

        // an untransported patient is added by the nearest route end, to the nearest hospital with room
        PECTOSolution without = PECTOTripEdit.unassign(SOLUTION, 4);
        assertNull(PECTOTripEdit.find(without, 4));
        PECTOSolution back = PECTOTripEdit.moveTrip(without, 4, 0, 0);
        assertArrayEquals(new int[]{4, 6, 2, 6, 3, 6}, back.route(0).stops());
        // delivering an untransported patient appends it to the route that ends nearest (ambulance 0, at hospital 6)
        assertArrayEquals(new int[]{2, 6, 3, 6, 4, 7}, PECTOTripEdit.deliver(without, 4, 7).route(0).stops());
    }
}
