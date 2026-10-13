package problems.pecto.operators;

import base.MutationOperator;
import problems.pecto.PECTOSolution;
import problems.pecto.Route;
import util.random.RNG;

/**
 * Moves a random trip (a patient with the hospital it is delivered to) to a random position of a random route,
 * possibly the same one. Every patient keeps its hospital, so a feasible solution stays feasible. At most two routes
 * change, so the neighbor is evaluated in time proportional to their length.
 */
public final class MoveTrip implements MutationOperator<PECTOSolution> {

    @Override
    public PECTOSolution apply(PECTOSolution solution, RNG rng) {
        RandomTrip moved = RandomTrip.of(solution, rng);
        if (moved == null)
            return solution;
        Route from = solution.route(moved.ambulance);
        int patient = from.patient(moved.trip), hospital = from.hospital(moved.trip);
        Route remaining = from.withoutTrip(moved.trip);

        int target = rng.randInt(solution.routeCount());
        if (target == moved.ambulance)
            return solution.withRoutes(remaining.withTrip(rng.randInt(remaining.tripCount() + 1), patient, hospital));
        Route to = solution.route(target);
        return solution.withRoutes(remaining, to.withTrip(rng.randInt(to.tripCount() + 1), patient, hospital));
    }

    @Override
    public String toString() {
        return "MoveTrip";
    }
}
