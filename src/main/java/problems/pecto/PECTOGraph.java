package problems.pecto;

import metaheuristic.aco.GraphProblem;
import representation.IntegerPermutation;

/**
 * PECTO as a graph problem, so that graph algorithms such as ACO can solve it. A solution is a "giant tour": a
 * permutation of the ambulances and patients (node IDs are their participant IDs), read as a cycle and cut at the
 * ambulances, so that every ambulance transports the patients that follow it up to the next ambulance. Hospitals
 * are not nodes: {@link #decode} delivers each patient, in tour order, to the hospital with a free bed that
 * minimizes the delivery leg plus the leg to the next patient. Every tour decodes to a feasible solution, and the
 * cost of a tour is the cost of its decoded solution.
 * <p>
 * {@link #getDistance} only guides the construction of tours: the urgency-weighted cost of the step from node i to
 * node j, a patient i being first delivered to its nearest hospital. Stepping to an ambulance ends a route and starts
 * the next one there; it is weighted like picking up a patient of average urgency, so that routes change over to
 * ambulances nearby. Immutable and thread-safe.
 */
public final class PECTOGraph implements GraphProblem {

    private final PECTOProblem problem;
    private final int[] nearestHospital;    // by patient index
    private final double meanWeight;        // mean urgency weight of the patients

    public PECTOGraph(PECTOProblem problem) {
        this.problem = problem;
        int patients = problem.patientCount();
        nearestHospital = new int[patients];
        double weights = 0;
        for (int k = 0; k < patients; k++) {
            int patient = problem.patientId(k);
            nearestHospital[k] = nearest(patient);
            weights += problem.urgencyWeight(patient);
        }
        meanWeight = patients == 0 ? 1 : weights / patients;
    }

    private int nearest(int patient) {
        int best = problem.hospitalId(0);
        for (int k = 1; k < problem.hospitalCount(); k++)
            if (problem.distance(patient, problem.hospitalId(k)) < problem.distance(patient, best))
                best = problem.hospitalId(k);
        return best;
    }

    public PECTOProblem problem() {
        return problem;
    }

    // the ambulances and the patients
    @Override
    public int getNodeCount() {
        return problem.ambulanceCount() + problem.patientCount();
    }

    @Override
    public double getDistance(int from, int to) {
        double cost = 0;
        int at = from;
        if (problem.isPatient(from)) {
            at = nearestHospital[problem.index(from)];
            cost = problem.urgencyWeight(from) * problem.distance(from, at);
        }
        double weight = problem.isPatient(to) ? problem.urgencyWeight(to) : meanWeight;
        return cost + weight * problem.distance(at, to);
    }

    // The solution the tour stands for (see the class comment); the tour must be a permutation of the nodes
    public PECTOSolution decode(IntegerPermutation tour) {
        int n = getNodeCount();
        if (tour.getLength() != n)
            throw new IllegalArgumentException("expected a tour of " + n + " nodes, got " + tour.getLength());
        int start = 0;
        while (!problem.isAmbulance(tour.get(start)))
            start++;

        int[] room = new int[problem.hospitalCount()];
        for (int k = 0; k < room.length; k++)
            room[k] = problem.capacity(problem.hospitalId(k));
        int[][] stops = new int[problem.ambulanceCount()][];
        int[] patients = new int[problem.patientCount()];   // the patients of the current route
        int k = 0;
        while (k < n) {
            int ambulance = tour.get((start + k++) % n);
            int count = 0;
            while (k < n && !problem.isAmbulance(tour.get((start + k) % n)))
                patients[count++] = tour.get((start + k++) % n);
            stops[ambulance] = deliver(patients, count, room);
        }
        return problem.solution(stops);
    }

    // the stops of a route that transports the patients in this order, taking beds from room
    private int[] deliver(int[] patients, int count, int[] room) {
        int[] stops = new int[2 * count];
        for (int t = 0; t < count; t++) {
            int patient = patients[t];
            int next = t + 1 < count ? patients[t + 1] : -1;
            int best = -1;
            double bestCost = Double.POSITIVE_INFINITY;
            for (int h = 0; h < room.length; h++) {
                if (room[h] == 0)
                    continue;
                int hospital = problem.hospitalId(h);
                double cost = problem.urgencyWeight(patient) * problem.distance(patient, hospital)
                        + (next < 0 ? 0 : problem.urgencyWeight(next) * problem.distance(hospital, next));
                if (cost < bestCost) {
                    bestCost = cost;
                    best = h;
                }
            }
            room[best]--;   // a bed is always left: the hospitals can take all patients
            stops[2 * t] = patient;
            stops[2 * t + 1] = problem.hospitalId(best);
        }
        return stops;
    }

    @Override
    public double cost(IntegerPermutation tour) {
        return problem.cost(decode(tour));
    }

    // a permutation of the nodes
    @Override
    public boolean isFeasible(IntegerPermutation tour) {
        if (tour.getLength() != getNodeCount())
            return false;
        boolean[] seen = new boolean[getNodeCount()];
        for (int i = 0; i < tour.getLength(); i++) {
            int node = tour.get(i);
            if (node < 0 || node >= seen.length || seen[node])
                return false;
            seen[node] = true;
        }
        return true;
    }

    @Override
    public String toString() {
        return "PECTOGraph(" + problem + ")";
    }
}
