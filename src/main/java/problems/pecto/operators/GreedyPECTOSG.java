package problems.pecto.operators;

import base.InitialSolutionGenerator;
import base.OptimizationProblem;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Route;
import util.random.RNG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Greedy feasible solutions: the patients, most urgent first (ties by ID), are each picked up by the ambulance whose
 * route currently ends nearest to them and delivered to the nearest hospital with a free bed. Deterministic: the RNG
 * is not used.
 * <p>
 * {@link #complete} applies the same rule to the patients a partial solution does not transport, and {@link #repair}
 * first drops repeated trips and trips to hospitals beyond their capacity, so that any solution can be made feasible.
 */
public final class GreedyPECTOSG implements InitialSolutionGenerator<PECTOSolution> {

    private final PECTOProblem problem;

    public GreedyPECTOSG(PECTOProblem problem) {
        this.problem = problem;
    }

    @Override
    public PECTOSolution generate(OptimizationProblem<PECTOSolution> ignored, RNG rng) {
        return complete(problem.emptySolution());
    }

    /**
     * The solution with every patient it does not transport appended greedily to a route. Existing trips are kept, so
     * the result is feasible if the solution transports no patient twice and no hospital is over capacity; otherwise
     * a patient may be sent to the nearest hospital even though it has no free bed. Routes that do not change are
     * shared with the solution.
     */
    public static PECTOSolution complete(PECTOSolution solution) {
        PECTOProblem problem = solution.problem();
        boolean[] transported = new boolean[problem.patientCount()];
        for (Route route : solution.routes())
            for (int t = 0; t < route.tripCount(); t++)
                transported[problem.index(route.patient(t))] = true;
        List<Integer> missing = new ArrayList<>();
        for (int k = 0; k < transported.length; k++)
            if (!transported[k])
                missing.add(problem.patientId(k));
        if (missing.isEmpty())
            return solution;
        missing.sort(Comparator.comparingDouble(problem::urgencyWeight).thenComparingInt(Integer::intValue));

        int ambulances = problem.ambulanceCount();
        int[] end = new int[ambulances];                    // where each route ends now
        List<List<Integer>> added = new ArrayList<>();      // the stops appended to each route
        for (int a = 0; a < ambulances; a++) {
            end[a] = solution.route(a).end();
            added.add(new ArrayList<>());
        }
        int[] room = new int[problem.hospitalCount()];
        for (int k = 0; k < room.length; k++)
            room[k] = problem.room(solution, problem.hospitalId(k));

        for (int patient : missing) {
            int ambulance = 0;
            for (int a = 1; a < ambulances; a++)
                if (problem.distance(end[a], patient) < problem.distance(end[ambulance], patient))
                    ambulance = a;
            int hospital = nearestHospital(problem, patient, room);
            room[problem.index(hospital)]--;
            added.get(ambulance).add(patient);
            added.get(ambulance).add(hospital);
            end[ambulance] = hospital;
        }

        List<Route> changed = new ArrayList<>();
        for (int a = 0; a < ambulances; a++)
            if (!added.get(a).isEmpty()) {
                Route route = solution.route(a);
                int[] stops = Arrays.copyOf(route.stops(), 2 * route.tripCount() + added.get(a).size());
                for (int k = 0; k < added.get(a).size(); k++)
                    stops[2 * route.tripCount() + k] = added.get(a).get(k);
                changed.add(problem.route(a, stops));
            }
        return solution.withRoutes(changed.toArray(new Route[0]));
    }

    /**
     * A feasible solution close to the given one: the trips are kept in route order (ambulance 0 first) as long as
     * their patient has not been transported yet and their hospital has a free bed left; the patients left without
     * a trip are then added by {@link #complete}.
     */
    public static PECTOSolution repair(PECTOSolution solution) {
        PECTOProblem problem = solution.problem();
        boolean[] transported = new boolean[problem.patientCount()];
        int[] room = new int[problem.hospitalCount()];
        for (int k = 0; k < room.length; k++)
            room[k] = problem.capacity(problem.hospitalId(k));
        List<Route> changed = new ArrayList<>();
        for (Route route : solution.routes()) {
            int[] kept = new int[2 * route.tripCount()];
            int n = 0;
            for (int t = 0; t < route.tripCount(); t++) {
                int p = problem.index(route.patient(t)), h = problem.index(route.hospital(t));
                if (!transported[p] && room[h] > 0) {
                    transported[p] = true;
                    room[h]--;
                    kept[n++] = route.patient(t);
                    kept[n++] = route.hospital(t);
                }
            }
            if (n < kept.length)
                changed.add(problem.route(route.ambulance(), Arrays.copyOf(kept, n)));
        }
        return complete(changed.isEmpty() ? solution : solution.withRoutes(changed.toArray(new Route[0])));
    }

    // the nearest hospital with a free bed, or the nearest of all if none has one
    private static int nearestHospital(PECTOProblem problem, int patient, int[] room) {
        int nearest = -1, nearestWithRoom = -1;
        for (int k = 0; k < room.length; k++) {
            int hospital = problem.hospitalId(k);
            double d = problem.distance(patient, hospital);
            if (nearest < 0 || d < problem.distance(patient, nearest))
                nearest = hospital;
            if (room[k] > 0 && (nearestWithRoom < 0 || d < problem.distance(patient, nearestWithRoom)))
                nearestWithRoom = hospital;
        }
        return nearestWithRoom >= 0 ? nearestWithRoom : nearest;
    }

    @Override
    public String toString() {
        return "GreedyPECTOSG";
    }
}
