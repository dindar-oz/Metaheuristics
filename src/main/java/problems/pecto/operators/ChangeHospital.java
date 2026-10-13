package problems.pecto.operators;

import base.MutationOperator;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Route;
import util.random.RNG;

/**
 * Delivers the patient of a random trip to another hospital, chosen uniformly among those with a free bed, so a
 * feasible solution stays feasible. Without this move the hospital of every patient would stay as it was first set.
 * One route changes.
 */
public final class ChangeHospital implements MutationOperator<PECTOSolution> {

    @Override
    public PECTOSolution apply(PECTOSolution solution, RNG rng) {
        RandomTrip changed = RandomTrip.of(solution, rng);
        if (changed == null)
            return solution;
        PECTOProblem problem = solution.problem();
        Route route = solution.route(changed.ambulance);
        int current = route.hospital(changed.trip);

        // the k-th other hospital with room, for k drawn uniformly
        int candidates = 0;
        for (int k = 0; k < problem.hospitalCount(); k++)
            if (isCandidate(solution, problem.hospitalId(k), current))
                candidates++;
        if (candidates == 0)
            return solution;
        int pick = rng.randInt(candidates);
        for (int k = 0; ; k++) {
            int hospital = problem.hospitalId(k);
            if (isCandidate(solution, hospital, current) && pick-- == 0)
                return solution.withRoutes(route.withHospital(changed.trip, hospital));
        }
    }

    private static boolean isCandidate(PECTOSolution solution, int hospital, int current) {
        return hospital != current && solution.problem().room(solution, hospital) > 0;
    }

    @Override
    public String toString() {
        return "ChangeHospital";
    }
}
