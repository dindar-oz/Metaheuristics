package problems.pecto.operators;

import base.MutationOperator;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import util.random.RNG;

/**
 * Exchanges two random trips, each with the hospital its patient is delivered to, between two routes or within one.
 * Every hospital keeps its load, so a feasible solution stays feasible. At most two routes change.
 */
public final class SwapTrips implements MutationOperator<PECTOSolution> {

    @Override
    public PECTOSolution apply(PECTOSolution solution, RNG rng) {
        int trips = solution.tripCount();
        if (trips < 2)
            return solution;
        int i = rng.randInt(trips), j = rng.randInt(trips - 1);
        if (j >= i)
            j++;
        RandomTrip first = RandomTrip.at(solution, i), second = RandomTrip.at(solution, j);
        PECTOProblem problem = solution.problem();

        int[] a = solution.route(first.ambulance).stops();
        if (first.ambulance == second.ambulance) {
            swap(a, 2 * first.trip, a, 2 * second.trip);
            return solution.withRoutes(problem.route(first.ambulance, a));
        }
        int[] b = solution.route(second.ambulance).stops();
        swap(a, 2 * first.trip, b, 2 * second.trip);
        return solution.withRoutes(problem.route(first.ambulance, a), problem.route(second.ambulance, b));
    }

    // exchanges the patient-hospital pairs at x[i], x[i+1] and y[j], y[j+1]
    private static void swap(int[] x, int i, int[] y, int j) {
        for (int k = 0; k < 2; k++) {
            int tmp = x[i + k];
            x[i + k] = y[j + k];
            y[j + k] = tmp;
        }
    }

    @Override
    public String toString() {
        return "SwapTrips";
    }
}
