package metaheuristic.hybridga;

import metaheuristic.Params;
import metaheuristic.ea.base.CrossOverOperator;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Route;
import util.random.RNG;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Alternating route crossover (ARX) of the research hybrid GA. Every ambulance of the child takes its route from the
 * parent where that route is cheaper, with probability {@code cheaperProbability}, and from the other parent
 * otherwise. The routes are copied in ambulance order; a trip whose patient is already transported or whose hospital
 * is already full is dropped (copy and prune), and a route copied without losses is shared with its parent, keeping its
 * cached cost.
 * <p>
 * Every patient left without a trip is then repaired, in patient order: it goes to the nearest hospital with a free
 * bed, on the route of the ambulance that is nearest to it (by its start or by the current end of its route), at the
 * position of that route where the added distance is smallest. As in the research code, the added distance is
 * measured unweighted. The child is always feasible.
 * <p>
 * For ablation, both domain-aware parts can be switched off: {@code cheaperProbability} 0.5 picks the parent of every
 * route at random, and {@code bestInsertion} false repairs a missing patient on a random route at a random position
 * (still delivered to the nearest hospital with a free bed).
 */
public final class AlternatingRouteCrossover implements CrossOverOperator<PECTOSolution> {

    public static final double DEFAULT_CHEAPER_PROBABILITY = 0.75;

    private final double cheaperProbability;
    private final boolean bestInsertion;

    public AlternatingRouteCrossover(double cheaperProbability, boolean bestInsertion) {
        this.cheaperProbability = Params.probability("cheaperProbability", cheaperProbability);
        this.bestInsertion = bestInsertion;
    }

    public AlternatingRouteCrossover(double cheaperProbability) {
        this(cheaperProbability, true);
    }

    public AlternatingRouteCrossover() {
        this(DEFAULT_CHEAPER_PROBABILITY);
    }

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

        // copy and prune
        Route[] child = new Route[ambulances];
        for (int a = 0; a < ambulances; a++) {
            Route mine = first.route(a), theirs = second.route(a);
            boolean firstIsCheaper = problem.routeCost(mine) <= problem.routeCost(theirs);
            boolean takeFirst = rng.rollDice(cheaperProbability) == firstIsCheaper;
            child[a] = copy(takeFirst ? mine : theirs, transported, room, problem);
        }

        // repair: best (or random) insertion of the missing patients
        for (int k = 0; k < transported.length; k++)
            if (!transported[k]) {
                int patient = problem.patientId(k);
                int hospital = nearestWithRoom(problem, patient, room);
                room[problem.index(hospital)]--;
                if (bestInsertion) {
                    int a = nearestAmbulance(problem, child, patient);
                    child[a] = child[a].withTrip(bestPosition(problem, child[a], patient, hospital), patient, hospital);
                } else {
                    int a = rng.randInt(ambulances);
                    child[a] = child[a].withTrip(rng.randInt(child[a].tripCount() + 1), patient, hospital);
                }
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

    // the ambulance whose start or route end is nearest to the patient (ties: the lowest ID)
    private static int nearestAmbulance(PECTOProblem problem, Route[] routes, int patient) {
        int nearest = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int a = 0; a < routes.length; a++) {
            double d = Math.min(problem.distance(a, patient), problem.distance(routes[a].end(), patient));
            if (d < best) {
                best = d;
                nearest = a;
            }
        }
        return nearest;
    }

    // the trip position (0..tripCount) where a trip to the patient and on to the hospital adds the least distance
    private static int bestPosition(PECTOProblem problem, Route route, int patient, int hospital) {
        int best = 0;
        double bestAdded = Double.POSITIVE_INFINITY;
        double delivery = problem.distance(patient, hospital);
        for (int t = 0; t <= route.tripCount(); t++) {
            int previous = t == 0 ? route.ambulance() : route.hospital(t - 1);
            double added = problem.distance(previous, patient) + delivery;
            if (t < route.tripCount()) {
                int next = route.patient(t);
                added += problem.distance(hospital, next) - problem.distance(previous, next);
            }
            if (added < bestAdded) {
                bestAdded = added;
                best = t;
            }
        }
        return best;
    }

    @Override
    public String toString() {
        return "ARX(" + cheaperProbability + (bestInsertion ? "" : ", random repair") + ")";
    }
}
