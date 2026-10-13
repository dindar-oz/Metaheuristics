package lab.problemlab.pecto;

import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Route;

/**
 * Hand edits of a PECTO solution, as the Problem Lab offers them: moving a trip (a patient with its hospital) to
 * another route or position, delivering a patient elsewhere, dropping a trip, and whole-route changes. An edit
 * returns a new solution (or the same one if nothing changes) and never checks feasibility: the lab shows the
 * violations an edit causes instead of refusing it.
 */
final class PECTOTripEdit {

    // the trip-th trip of the ambulance's route
    record Location(int ambulance, int trip) {
    }

    private PECTOTripEdit() {
    }

    // where the patient is picked up (the first such trip), null if no route transports it
    static Location find(PECTOSolution solution, int patient) {
        for (Route route : solution.routes())
            for (int t = 0; t < route.tripCount(); t++)
                if (route.patient(t) == patient)
                    return new Location(route.ambulance(), t);
        return null;
    }

    // the solution without the patient's trip
    static PECTOSolution unassign(PECTOSolution solution, int patient) {
        Location at = find(solution, patient);
        return at == null ? solution : solution.withRoutes(solution.route(at.ambulance()).withoutTrip(at.trip()));
    }

    /**
     * Moves the patient's trip, with its hospital, to the given position of the ambulance's route (counted after the
     * trip is taken out; -1 or a position past the end appends it). An untransported patient gets a new trip to
     * {@link #bestHospital}.
     */
    static PECTOSolution moveTrip(PECTOSolution solution, int patient, int ambulance, int position) {
        Location from = find(solution, patient);
        if (from == null)
            return insert(solution, ambulance, position, patient, bestHospital(solution, patient));
        Route source = solution.route(from.ambulance());
        int hospital = source.hospital(from.trip());
        Route remaining = source.withoutTrip(from.trip());
        if (from.ambulance() == ambulance) {
            int at = clamp(position, remaining.tripCount());
            return at == from.trip() ? solution : solution.withRoutes(remaining.withTrip(at, patient, hospital));
        }
        Route target = solution.route(ambulance);
        return solution.withRoutes(remaining, target.withTrip(clamp(position, target.tripCount()), patient, hospital));
    }

    // moves the patient's trip just before the trip of another patient, in that patient's route
    static PECTOSolution insertBefore(PECTOSolution solution, int patient, int other) {
        Location target = find(solution, other), from = find(solution, patient);
        if (target == null || patient == other)
            return solution;
        int position = target.trip();
        if (from != null && from.ambulance() == target.ambulance() && from.trip() < target.trip())
            position--;                 // the target moves up once the patient's trip is taken out
        return moveTrip(solution, patient, target.ambulance(), position);
    }

    // delivers the patient to the hospital; an untransported patient is picked up by the ambulance whose route ends nearest
    static PECTOSolution deliver(PECTOSolution solution, int patient, int hospital) {
        Location at = find(solution, patient);
        if (at == null)
            return insert(solution, nearestEnd(solution, patient), -1, patient, hospital);
        Route route = solution.route(at.ambulance());
        return route.hospital(at.trip()) == hospital ? solution : solution.withRoutes(route.withHospital(at.trip(), hospital));
    }

    // moves the patient's trip delta positions earlier (negative) or later within its route
    static PECTOSolution shift(PECTOSolution solution, int patient, int delta) {
        Location at = find(solution, patient);
        if (at == null)
            return solution;
        Route route = solution.route(at.ambulance());
        int to = Math.max(0, Math.min(route.tripCount() - 1, at.trip() + delta));
        return to == at.trip() ? solution
                : solution.withRoutes(route.withoutTrip(at.trip()).withTrip(to, patient, route.hospital(at.trip())));
    }

    // the ambulance's route without trips
    static PECTOSolution clearRoute(PECTOSolution solution, int ambulance) {
        return solution.route(ambulance).isEmpty() ? solution : solution.withRoutes(solution.problem().route(ambulance));
    }

    // the ambulance's trips in the opposite order (each patient keeps its hospital)
    static PECTOSolution reverseRoute(PECTOSolution solution, int ambulance) {
        Route route = solution.route(ambulance);
        if (route.tripCount() < 2)
            return solution;
        int[] stops = route.stops(), reversed = new int[stops.length];
        for (int t = 0; t < route.tripCount(); t++) {
            int from = 2 * (route.tripCount() - 1 - t);
            reversed[2 * t] = stops[from];
            reversed[2 * t + 1] = stops[from + 1];
        }
        return solution.withRoutes(solution.problem().route(ambulance, reversed));
    }

    // the nearest hospital with a free bed, or the nearest of all if every hospital is full
    static int bestHospital(PECTOSolution solution, int patient) {
        PECTOProblem problem = solution.problem();
        int best = -1, nearest = -1;
        for (int k = 0; k < problem.hospitalCount(); k++) {
            int h = problem.hospitalId(k);
            double d = problem.distance(patient, h);
            if (nearest < 0 || d < problem.distance(patient, nearest))
                nearest = h;
            if (problem.room(solution, h) > 0 && (best < 0 || d < problem.distance(patient, best)))
                best = h;
        }
        return best >= 0 ? best : nearest;
    }

    // the ambulance whose route ends nearest to the participant
    static int nearestEnd(PECTOSolution solution, int id) {
        PECTOProblem problem = solution.problem();
        int best = 0;
        for (int a = 1; a < problem.ambulanceCount(); a++)
            if (problem.distance(solution.route(a).end(), id) < problem.distance(solution.route(best).end(), id))
                best = a;
        return best;
    }

    private static PECTOSolution insert(PECTOSolution solution, int ambulance, int position, int patient, int hospital) {
        Route route = solution.route(ambulance);
        return solution.withRoutes(route.withTrip(clamp(position, route.tripCount()), patient, hospital));
    }

    private static int clamp(int position, int count) {
        return position < 0 || position > count ? count : position;
    }
}
