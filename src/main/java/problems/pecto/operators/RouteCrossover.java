package problems.pecto.operators;

import metaheuristic.ea.base.CrossOverOperator;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Route;
import util.random.RNG;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Uniform route crossover: every ambulance of the child takes its route from one of the two parents, chosen at
 * random. A patient that two of the chosen routes transport keeps only the trip of the route copied first, and a trip
 * to a hospital that is already full is dropped; a route copied without losses is shared with its parent, and so
 * keeps its cached cost. Each patient left without a trip is then delivered to the nearest hospital with a free bed,
 * by a random ambulance at a random position of its route. The child is always feasible.
 */
public final class RouteCrossover implements CrossOverOperator<PECTOSolution> {

    @Override
    public List<PECTOSolution> apply(PECTOSolution first, PECTOSolution second, RNG rng) {
        PECTOProblem problem = first.problem();
        if (second.problem() != problem)
            throw new IllegalArgumentException("the parents belong to different problems");
        int ambulances = problem.ambulanceCount();
        boolean[] transported = new boolean[problem.patientCount()];   // by patient index
        int[] room = new int[problem.hospitalCount()];                 // free beds, by hospital index
        for (int k = 0; k < room.length; k++)
            room[k] = problem.capacity(problem.hospitalId(k));

        // copied in random order, so that no ambulance is favored when two chosen routes share a patient
        Route[] child = new Route[ambulances];
        for (int a : shuffledIndices(ambulances, rng))
            child[a] = copy((rng.randInt(2) == 0 ? first : second).route(a), transported, room, problem);

        for (int k = 0; k < transported.length; k++)
            if (!transported[k]) {
                int patient = problem.patientId(k);
                int hospital = nearestWithRoom(problem, patient, room);
                room[problem.index(hospital)]--;
                int a = rng.randInt(ambulances);
                child[a] = child[a].withTrip(rng.randInt(child[a].tripCount() + 1), patient, hospital);
            }
        return Collections.singletonList(problem.emptySolution().withRoutes(child));
    }

    // the trips of the route whose patient is not transported yet and whose hospital has room
    private static Route copy(Route route, boolean[] transported, int[] room, PECTOProblem problem) {
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
        return n == kept.length ? route : problem.route(route.ambulance(), Arrays.copyOf(kept, n));
    }

    // there is always one: the hospitals can take all patients, and fewer than all are transported so far
    private static int nearestWithRoom(PECTOProblem problem, int patient, int[] room) {
        int nearest = -1;
        double best = Double.POSITIVE_INFINITY;
        for (int k = 0; k < room.length; k++) {
            double d = problem.distance(patient, problem.hospitalId(k));
            if (room[k] > 0 && d < best) {
                best = d;
                nearest = problem.hospitalId(k);
            }
        }
        return nearest;
    }

    private static int[] shuffledIndices(int n, RNG rng) {
        int[] values = new int[n];
        for (int i = 0; i < n; i++)
            values[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = rng.randInt(i + 1);
            int tmp = values[i];
            values[i] = values[j];
            values[j] = tmp;
        }
        return values;
    }

    @Override
    public String toString() {
        return "RouteCrossover";
    }
}
