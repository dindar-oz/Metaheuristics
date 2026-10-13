package problems.pecto.operators;

import problems.pecto.PECTOSolution;
import util.random.RNG;

/**
 * A trip drawn uniformly from all trips of a solution: the ambulance whose route has it and its index in the route.
 */
final class RandomTrip {

    final int ambulance;
    final int trip;

    private RandomTrip(int ambulance, int trip) {
        this.ambulance = ambulance;
        this.trip = trip;
    }

    // null if no ambulance has a trip
    static RandomTrip of(PECTOSolution solution, RNG rng) {
        if (solution.tripCount() == 0)
            return null;
        return at(solution, rng.randInt(solution.tripCount()));
    }

    // the k-th trip of the solution, counting the routes in ambulance order (0 <= k < tripCount())
    static RandomTrip at(PECTOSolution solution, int k) {
        int a = 0;
        while (k >= solution.route(a).tripCount())
            k -= solution.route(a++).tripCount();
        return new RandomTrip(a, k);
    }
}
