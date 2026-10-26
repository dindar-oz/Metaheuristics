package metaheuristic.hybridga;

import base.MutationOperator;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import util.random.RNG;

/**
 * The swap mutation of the research hybrid GA: two participants of the same type swap places, either two patients
 * (each is then delivered to the other's hospital) or two hospitals (each patient goes to the other's hospital). Both
 * routes are drawn uniformly among the routes with trips and may be the same route; the type is drawn with
 * probability 1/2. Unlike {@link problems.pecto.operators.SwapTrips}, a trip's patient and hospital are separated.
 * Every hospital keeps its load, so a feasible solution stays feasible. At most two routes change.
 */
public final class SwapParticipants implements MutationOperator<PECTOSolution> {

    @Override
    public PECTOSolution apply(PECTOSolution solution, RNG rng) {
        int busy = 0;
        for (int a = 0; a < solution.routeCount(); a++)
            if (!solution.route(a).isEmpty())
                busy++;
        if (busy == 0)
            return solution;
        int first = busyRoute(solution, rng.randInt(busy)), second = busyRoute(solution, rng.randInt(busy));
        int offset = rng.randInt(2);        // 0: swap the patients, 1: swap the hospitals
        int i = 2 * rng.randInt(solution.route(first).tripCount()) + offset;
        int j = 2 * rng.randInt(solution.route(second).tripCount()) + offset;
        if (first == second && i == j)
            return solution;

        PECTOProblem problem = solution.problem();
        int[] a = solution.route(first).stops();
        if (first == second) {
            int tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
            return solution.withRoutes(problem.route(first, a));
        }
        int[] b = solution.route(second).stops();
        int tmp = a[i];
        a[i] = b[j];
        b[j] = tmp;
        return solution.withRoutes(problem.route(first, a), problem.route(second, b));
    }

    // the ambulance of the k-th route with trips
    private static int busyRoute(PECTOSolution solution, int k) {
        for (int a = 0; ; a++)
            if (!solution.route(a).isEmpty() && k-- == 0)
                return a;
    }

    @Override
    public String toString() {
        return "SwapParticipants";
    }
}
