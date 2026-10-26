package metaheuristic.hybridga;

import base.MutationOperator;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Route;
import util.random.RNG;

/**
 * The offspring mutation of the research hybrid GA ("stagnation move"): a random trip of the most expensive route moves,
 * with its hospital, to a random position of the least expensive other route. It shifts work from the ambulances that
 * cost most to those that cost least. Every patient keeps its hospital, so a feasible solution stays feasible; two
 * routes change. Ties go to the lowest ambulance ID.
 */
public final class CostliestRouteMove implements MutationOperator<PECTOSolution> {

    @Override
    public PECTOSolution apply(PECTOSolution solution, RNG rng) {
        PECTOProblem problem = solution.problem();
        if (solution.tripCount() == 0 || solution.routeCount() < 2)
            return solution;

        int costliest = -1;
        for (int a = 0; a < solution.routeCount(); a++)
            if (!solution.route(a).isEmpty()
                    && (costliest < 0 || problem.routeCost(solution.route(a)) > problem.routeCost(solution.route(costliest))))
                costliest = a;
        int cheapest = -1;
        for (int a = 0; a < solution.routeCount(); a++)
            if (a != costliest
                    && (cheapest < 0 || problem.routeCost(solution.route(a)) < problem.routeCost(solution.route(cheapest))))
                cheapest = a;

        Route from = solution.route(costliest), to = solution.route(cheapest);
        int trip = rng.randInt(from.tripCount());
        int patient = from.patient(trip), hospital = from.hospital(trip);
        return solution.withRoutes(from.withoutTrip(trip), to.withTrip(rng.randInt(to.tripCount() + 1), patient, hospital));
    }

    @Override
    public String toString() {
        return "CostliestRouteMove";
    }
}
