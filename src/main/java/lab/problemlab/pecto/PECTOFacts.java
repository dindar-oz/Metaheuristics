package lab.problemlab.pecto;

import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Route;

import java.util.Arrays;

/**
 * What the PECTO views show about a solution, computed once per state: who transports each patient, the cost of every
 * route and trip, and the constraint violations (Eqs. 12-13). Without a solution only the problem is known.
 */
final class PECTOFacts {

    final PECTOProblem problem;
    final PECTOSolution solution;   // null if there is none
    final int[] carrier;            // by patient index: the ambulance of the (first) trip to the patient, -1 if none
    final int[] trip;               // by patient index: that trip's index in the route
    final int[] visits;             // by patient index: the number of trips to the patient
    final double[] routeCost;       // by ambulance
    final double cost;
    final int missing, duplicated, overfull, busy, longest;

    PECTOFacts(PECTOProblem problem, PECTOSolution solution) {
        this.problem = problem;
        this.solution = solution;
        int patients = problem.patientCount();
        carrier = new int[patients];
        trip = new int[patients];
        visits = new int[patients];
        routeCost = new double[problem.ambulanceCount()];
        Arrays.fill(carrier, -1);
        int busyCount = 0, longestRoute = 0;
        if (solution != null)
            for (int a = 0; a < routeCost.length; a++) {
                Route route = solution.route(a);
                routeCost[a] = problem.routeCost(route);
                busyCount += route.isEmpty() ? 0 : 1;
                longestRoute = Math.max(longestRoute, route.tripCount());
                for (int t = route.tripCount() - 1; t >= 0; t--) {     // backwards, so the first trip is kept
                    int p = problem.index(route.patient(t));
                    if (carrier[p] < 0 || carrier[p] == a) {
                        carrier[p] = a;
                        trip[p] = t;
                    }
                    visits[p]++;
                }
            }
        cost = solution == null ? 0 : problem.cost(solution);
        busy = busyCount;
        longest = longestRoute;
        int m = 0, d = 0, o = 0;
        for (int v : visits) {
            m += v == 0 ? 1 : 0;
            d += v > 1 ? 1 : 0;
        }
        if (solution != null)
            for (int k = 0; k < problem.hospitalCount(); k++)
                o += problem.room(solution, problem.hospitalId(k)) < 0 ? 1 : 0;
        missing = solution == null ? 0 : m;
        duplicated = d;
        overfull = o;
    }

    boolean hasSolution() {
        return solution != null;
    }

    // the ambulance that picks the patient up, -1 if none
    int carrierOf(int patient) {
        return carrier[problem.index(patient)];
    }

    // the index of the patient's trip in its carrier's route
    int tripOf(int patient) {
        return trip[problem.index(patient)];
    }

    int visitsOf(int patient) {
        return visits[problem.index(patient)];
    }

    // where the ambulance is when it starts the given trip: its start or the previous trip's hospital
    int tripStart(Route route, int trip) {
        return trip == 0 ? route.ambulance() : route.hospital(trip - 1);
    }

    double tripCost(Route route, int trip) {
        return problem.tripCost(tripStart(route, trip), route.patient(trip), route.hospital(trip));
    }

    // patients delivered to the hospital (0 without a solution)
    int load(int hospital) {
        return solution == null ? 0 : solution.load(hospital);
    }
}
