package problems.pecto;

import java.util.Arrays;

/**
 * The route R_a of one ambulance (Eq. 9): the ambulance, followed by its trips in travel order. A trip goes to a
 * patient and then delivers that patient to a hospital, so the stops alternate patient, hospital, patient, hospital,
 * ... An ambulance without trips has an empty route.
 * <p>
 * Immutable; created by {@link PECTOProblem#route}, which checks the structure. A route keeps its cost once computed
 * ({@link PECTOProblem#routeCost}), so solutions that share a route evaluate it only once. The cached cost is not
 * part of the route's value.
 */
public final class Route {

    private final PECTOProblem problem;
    private final int ambulance;
    private final int[] stops;          // patient, hospital, patient, hospital, ... (participant IDs); never modified
    private volatile Double cost;       // null until computed by the problem

    Route(PECTOProblem problem, int ambulance, int[] stops) {
        this.problem = problem;
        this.ambulance = ambulance;
        this.stops = stops;
    }

    public PECTOProblem problem() {
        return problem;
    }

    // the ID of the ambulance, where the route starts
    public int ambulance() {
        return ambulance;
    }

    // the number of patients transported
    public int tripCount() {
        return stops.length / 2;
    }

    public boolean isEmpty() {
        return stops.length == 0;
    }

    // the patient picked up on the given trip (0-based)
    public int patient(int trip) {
        return stops[2 * trip];
    }

    // the hospital the patient of the given trip is delivered to
    public int hospital(int trip) {
        return stops[2 * trip + 1];
    }

    // where the route ends: the last hospital, or the ambulance itself if the route is empty
    public int end() {
        return stops.length == 0 ? ambulance : stops[stops.length - 1];
    }

    // the stops after the ambulance: patient, hospital, patient, hospital, ...
    public int[] stops() {
        return stops.clone();
    }

    // ---- derived routes (this route is not changed)

    // this route without the given trip
    public Route withoutTrip(int trip) {
        checkTrip(trip, tripCount() - 1);
        int[] next = new int[stops.length - 2];
        System.arraycopy(stops, 0, next, 0, 2 * trip);
        System.arraycopy(stops, 2 * trip + 2, next, 2 * trip, stops.length - 2 * trip - 2);
        return new Route(problem, ambulance, next);
    }

    // this route with a trip to the patient and on to the hospital inserted as the given trip (tripCount() = last)
    public Route withTrip(int trip, int patient, int hospital) {
        checkTrip(trip, tripCount());
        if (!problem.isPatient(patient) || !problem.isHospital(hospital))
            throw new IllegalArgumentException("a trip goes to a patient and then to a hospital, got " + patient + ", " + hospital);
        int[] next = new int[stops.length + 2];
        System.arraycopy(stops, 0, next, 0, 2 * trip);
        next[2 * trip] = patient;
        next[2 * trip + 1] = hospital;
        System.arraycopy(stops, 2 * trip, next, 2 * trip + 2, stops.length - 2 * trip);
        return new Route(problem, ambulance, next);
    }

    // this route with the patient of the given trip delivered to another hospital
    public Route withHospital(int trip, int hospital) {
        checkTrip(trip, tripCount() - 1);
        if (!problem.isHospital(hospital))
            throw new IllegalArgumentException(hospital + " is not a hospital ID");
        int[] next = stops.clone();
        next[2 * trip + 1] = hospital;
        return new Route(problem, ambulance, next);
    }

    private static void checkTrip(int trip, int last) {
        if (trip < 0 || trip > last)
            throw new IndexOutOfBoundsException("trip " + trip + " is not in [0, " + last + "]");
    }

    // ---- for PECTOProblem and PECTOSolution

    int[] stopArray() {
        return stops;
    }

    Double cachedCost() {
        return cost;
    }

    void cacheCost(double cost) {
        this.cost = cost;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Route))
            return false;
        Route other = (Route) o;
        return problem == other.problem && ambulance == other.ambulance && Arrays.equals(stops, other.stops);
    }

    @Override
    public int hashCode() {
        return 31 * ambulance + Arrays.hashCode(stops);
    }

    // e.g. "0: 7>13 10>12" for ambulance 0 taking patient 7 to hospital 13, then patient 10 to hospital 12
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append(ambulance).append(':');
        for (int k = 0; k < stops.length; k += 2)
            sb.append(' ').append(stops[k]).append('>').append(stops[k + 1]);
        return sb.toString();
    }
}
