package problems.pecto;

import base.OptimizationProblem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Post-Earthquake Casualty Transport Optimization (PECTO), as defined by Balci &amp; Oz, "Post-Earthquake Casualty
 * Transport Optimization", Section II. Ambulances transport patients, one at a time, to hospitals of limited
 * capacity; the objective is to minimize the total urgency-weighted distance travelled.
 * <ul>
 * <li>Participants are numbered by type (Eq. 4): ambulances 0..A-1, patients A..A+P-1, hospitals A+P..A+P+H-1.</li>
 * <li>A solution ({@link PECTOSolution}) gives every ambulance a {@link Route}: trips that each go to a patient and
 *     then to a hospital. Every leg of a route therefore has exactly one patient as an end, and costs its Euclidean
 *     length times that patient's urgency weight (Eq. 8). A route costs the sum of its legs (Eq. 9) and a solution
 *     the sum of its routes (Eq. 10), which is minimized (Eq. 11).</li>
 * <li>A solution is feasible when every patient is transported exactly once (Eq. 12) and no hospital receives more
 *     patients than its capacity (Eq. 13). {@link #cost} does not check this; see {@link #isFeasible}.</li>
 * </ul>
 * Distances are computed from the coordinates on demand rather than stored: the full distance matrix of the largest
 * paper instance would take about 235 MB, and computing a distance is faster than reading it from a large table.
 * Route costs are cached in the routes, so a solution that shares routes with an evaluated one is evaluated in time
 * proportional to its new routes, with exactly the same result as a full evaluation.
 * <p>
 * Instances are built with {@link #builder()}, or generated as in the paper by {@link PECTOInstances}.
 * Immutable and thread-safe.
 */
public final class PECTOProblem implements OptimizationProblem<PECTOSolution> {

    private final int ambulances;
    private final int patients;
    private final int hospitals;
    private final double[] x;            // coordinates, by participant ID
    private final double[] y;
    private final Urgency[] urgency;     // by patient index (ID - A)
    private final double[] weight;       // w(U_p), by patient index
    private final int[] capacity;        // by hospital index (ID - A - P)
    private final int totalCapacity;
    private final Route[] emptyRoutes;   // the empty route of every ambulance

    private PECTOProblem(Builder b) {
        ambulances = b.ambulances.size();
        patients = b.patients.size();
        hospitals = b.hospitals.size();
        if (ambulances == 0 || hospitals == 0)
            throw new IllegalArgumentException("a PECTO problem needs at least one ambulance and one hospital");

        int n = ambulances + patients + hospitals;
        x = new double[n];
        y = new double[n];
        int id = 0;
        for (List<double[]> group : Arrays.asList(b.ambulances, b.patients, b.hospitals))
            for (double[] point : group) {
                x[id] = point[0];
                y[id] = point[1];
                id++;
            }

        urgency = b.urgencies.toArray(new Urgency[0]);
        weight = new double[patients];
        for (int p = 0; p < patients; p++)
            weight[p] = urgency[p].weight();

        capacity = b.capacities.stream().mapToInt(Integer::intValue).toArray();
        totalCapacity = Arrays.stream(capacity).sum();
        if (totalCapacity < patients)
            throw new IllegalArgumentException("the hospitals can take " + totalCapacity + " of the " + patients
                    + " patients: no solution is feasible");

        emptyRoutes = new Route[ambulances];
        for (int a = 0; a < ambulances; a++)
            emptyRoutes[a] = new Route(this, a, new int[0]);
    }

    public static Builder builder() {
        return new Builder();
    }

    // ---- participants (Eq. 4)

    public int ambulanceCount() {
        return ambulances;
    }

    public int patientCount() {
        return patients;
    }

    public int hospitalCount() {
        return hospitals;
    }

    public int participantCount() {
        return ambulances + patients + hospitals;
    }

    // the ID of the k-th ambulance (0-based)
    public int ambulanceId(int k) {
        return k;
    }

    // the ID of the k-th patient (0-based)
    public int patientId(int k) {
        return ambulances + k;
    }

    // the ID of the k-th hospital (0-based)
    public int hospitalId(int k) {
        return ambulances + patients + k;
    }

    public boolean isAmbulance(int id) {
        return id >= 0 && id < ambulances;
    }

    public boolean isPatient(int id) {
        return id >= ambulances && id < ambulances + patients;
    }

    public boolean isHospital(int id) {
        return id >= ambulances + patients && id < participantCount();
    }

    public ParticipantType typeOf(int id) {
        if (isAmbulance(id))
            return ParticipantType.AMBULANCE;
        if (isPatient(id))
            return ParticipantType.PATIENT;
        if (isHospital(id))
            return ParticipantType.HOSPITAL;
        throw new IllegalArgumentException("no participant has ID " + id);
    }

    // the position k of a participant among those of its type, e.g. patientId(index(p)) == p for a patient p
    public int index(int id) {
        switch (typeOf(id)) {
            case AMBULANCE:
                return id;
            case PATIENT:
                return id - ambulances;
            default:
                return id - ambulances - patients;
        }
    }

    public double x(int id) {
        return x[id];
    }

    public double y(int id) {
        return y[id];
    }

    // ---- parameters (Eqs. 5-7, 13)

    // D_ij: the Euclidean distance between two participants (symmetric)
    public double distance(int i, int j) {
        double dx = x[i] - x[j];
        double dy = y[i] - y[j];
        return Math.sqrt(dx * dx + dy * dy);
    }

    // U_p
    public Urgency urgency(int patient) {
        return urgency[patient - ambulances];
    }

    // w(U_p)
    public double urgencyWeight(int patient) {
        return weight[patient - ambulances];
    }

    // the number of patients the hospital can receive
    public int capacity(int hospital) {
        return capacity[hospital - ambulances - patients];
    }

    public int totalCapacity() {
        return totalCapacity;
    }

    // ---- objective (Eqs. 8-11)

    /**
     * The cost of one trip of an ambulance that is at {@code from} (its start or a hospital): the leg to the patient
     * plus the leg that delivers the patient to the hospital, both weighted by the patient's urgency (Eq. 8).
     * Not checked, for speed: {@code patient} must be a patient ID.
     */
    public double tripCost(int from, int patient, int hospital) {
        return weight[patient - ambulances] * (distance(from, patient) + distance(patient, hospital));
    }

    // Eq. 9: the sum of the costs of the route's legs; 0 for an empty route. Computed once per route.
    public double routeCost(Route route) {
        checkOwned(route);
        Double cached = route.cachedCost();
        if (cached != null)
            return cached;
        int[] stops = route.stopArray();
        int from = route.ambulance();
        double cost = 0;
        for (int k = 0; k < stops.length; k += 2) {
            cost += tripCost(from, stops[k], stops[k + 1]);
            from = stops[k + 1];
        }
        route.cacheCost(cost);
        return cost;
    }

    // Eq. 10: the sum of the route costs, always added in ambulance order so that cached and fresh evaluations agree
    @Override
    public double cost(PECTOSolution solution) {
        checkOwned(solution);
        double total = 0;
        for (int a = 0; a < ambulances; a++)
            total += routeCost(solution.route(a));
        return total;
    }

    // ---- constraints (Eqs. 12-13)

    // Every patient is transported exactly once (Eq. 12) and no hospital receives more patients than its capacity (Eq. 13)
    @Override
    public boolean isFeasible(PECTOSolution solution) {
        if (solution.problem() != this || solution.tripCount() != patients)
            return false;
        for (int k = 0; k < hospitals; k++)
            if (solution.load(hospitalId(k)) > capacity[k])
                return false;
        // as many trips as patients, so every patient is transported exactly once if none is transported twice
        boolean[] transported = new boolean[patients];
        for (int a = 0; a < ambulances; a++) {
            int[] stops = solution.route(a).stopArray();
            for (int k = 0; k < stops.length; k += 2) {
                int p = stops[k] - ambulances;
                if (transported[p])
                    return false;
                transported[p] = true;
            }
        }
        return true;
    }

    // the free beds left at the hospital in the solution (negative if it is over capacity)
    public int room(PECTOSolution solution, int hospital) {
        checkOwned(solution);
        return capacity(hospital) - solution.load(hospital);
    }

    // ---- solutions

    /**
     * The route of an ambulance with the given stops: patient, hospital, patient, hospital, ...
     * Throws IllegalArgumentException if the stops are not patient-hospital pairs.
     */
    public Route route(int ambulance, int... stops) {
        if (!isAmbulance(ambulance))
            throw new IllegalArgumentException(ambulance + " is not an ambulance ID");
        if (stops.length % 2 != 0)
            throw new IllegalArgumentException("route of ambulance " + ambulance + ": the stops must be patient-hospital pairs, got "
                    + stops.length + " stops");
        for (int k = 0; k < stops.length; k += 2) {
            if (!isPatient(stops[k]))
                throw new IllegalArgumentException("route of ambulance " + ambulance + ": stop " + k + " must be a patient, got " + stops[k]);
            if (!isHospital(stops[k + 1]))
                throw new IllegalArgumentException("route of ambulance " + ambulance + ": stop " + (k + 1) + " must be a hospital, got " + stops[k + 1]);
        }
        return stops.length == 0 ? emptyRoutes[ambulance] : new Route(this, ambulance, stops.clone());
    }

    // The solution in which stops[a] are the stops of ambulance a (see route())
    public PECTOSolution solution(int[][] stops) {
        if (stops.length != ambulances)
            throw new IllegalArgumentException("expected the stops of " + ambulances + " ambulances, got " + stops.length);
        Route[] routes = new Route[ambulances];
        for (int a = 0; a < ambulances; a++)
            routes[a] = route(a, stops[a]);
        return PECTOSolution.of(this, routes);
    }

    // The solution in which no ambulance moves (infeasible unless there are no patients)
    public PECTOSolution emptySolution() {
        return PECTOSolution.of(this, emptyRoutes.clone());
    }

    /**
     * Decodes the paper's encoding of a solution: the routes one after the other, each starting with its ambulance
     * ID, e.g. {@code 0 7 13 10 12 1 5 13 ...}. Every ambulance must start exactly one route, in any order.
     * Throws IllegalArgumentException for sequences that break these rules (infeasible in the paper's terms).
     */
    public PECTOSolution decode(int[] sequence) {
        if (sequence.length == 0 || !isAmbulance(sequence[0]))
            throw new IllegalArgumentException("the sequence must start with an ambulance ID");
        int[][] stops = new int[ambulances][];
        int start = 0;
        while (start < sequence.length) {
            int ambulance = sequence[start];
            int end = start + 1;
            while (end < sequence.length && !isAmbulance(sequence[end]))
                end++;
            if (stops[ambulance] != null)
                throw new IllegalArgumentException("ambulance " + ambulance + " starts two routes");
            stops[ambulance] = Arrays.copyOfRange(sequence, start + 1, end);
            start = end;
        }
        for (int a = 0; a < ambulances; a++)
            if (stops[a] == null)
                throw new IllegalArgumentException("ambulance " + a + " has no route");
        return solution(stops);
    }

    private void checkOwned(Route route) {
        if (route.problem() != this)
            throw new IllegalArgumentException("the route belongs to another problem");
    }

    private void checkOwned(PECTOSolution solution) {
        if (solution.problem() != this)
            throw new IllegalArgumentException("the solution belongs to another problem");
    }

    @Override
    public String toString() {
        return "PECTO(" + ambulances + " ambulances, " + patients + " patients, " + hospitals + " hospitals)";
    }

    /**
     * Collects the participants of a problem. IDs are assigned by type whatever the order of the calls: the k-th
     * ambulance, patient and hospital added get ambulanceId(k), patientId(k) and hospitalId(k).
     */
    public static final class Builder {

        private final List<double[]> ambulances = new ArrayList<>();
        private final List<double[]> patients = new ArrayList<>();
        private final List<double[]> hospitals = new ArrayList<>();
        private final List<Urgency> urgencies = new ArrayList<>();
        private final List<Integer> capacities = new ArrayList<>();

        private Builder() {
        }

        public Builder ambulance(double x, double y) {
            ambulances.add(point(x, y));
            return this;
        }

        public Builder patient(double x, double y, Urgency urgency) {
            if (urgency == null)
                throw new IllegalArgumentException("the urgency of a patient is required");
            patients.add(point(x, y));
            urgencies.add(urgency);
            return this;
        }

        public Builder hospital(double x, double y, int capacity) {
            if (capacity < 0)
                throw new IllegalArgumentException("capacity must not be negative");
            hospitals.add(point(x, y));
            capacities.add(capacity);
            return this;
        }

        private static double[] point(double x, double y) {
            if (!Double.isFinite(x) || !Double.isFinite(y))
                throw new IllegalArgumentException("coordinates must be finite");
            return new double[]{x, y};
        }

        // Throws IllegalArgumentException without an ambulance or a hospital, or if the hospitals cannot take all patients
        public PECTOProblem build() {
            return new PECTOProblem(this);
        }
    }
}
