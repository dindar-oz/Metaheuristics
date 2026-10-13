package problems.pecto;

import representation.base.Representation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A solution of a {@link PECTOProblem}: one {@link Route} per ambulance, routes[a] being the route of ambulance a.
 * <p>
 * The paper encodes a solution as one integer sequence, the routes one after the other, each starting with its
 * ambulance ID: {@link #toSequence()} and {@link PECTOProblem#decode} convert between the two forms.
 * <p>
 * Immutable: {@link #withRoutes} returns a new solution that shares the unchanged routes, and with them their cached
 * costs, so such a neighbor is evaluated in time proportional to the routes that changed. The number of patients
 * each hospital receives is kept up to date the same way. Created by the problem ({@link PECTOProblem#solution},
 * {@link PECTOProblem#emptySolution()}, {@link PECTOProblem#decode}).
 */
public final class PECTOSolution implements Representation<PECTOSolution> {

    private final PECTOProblem problem;
    private final Route[] routes; // routes[a] is the route of ambulance a; never modified
    private final int[] loads;    // patients delivered to each hospital, by hospital index; never modified
    private final int trips;      // total number of trips

    private PECTOSolution(PECTOProblem problem, Route[] routes, int[] loads, int trips) {
        this.problem = problem;
        this.routes = routes;
        this.loads = loads;
        this.trips = trips;
    }

    // the solution made of the given routes, routes[a] being a route of ambulance a of the problem
    static PECTOSolution of(PECTOProblem problem, Route[] routes) {
        int[] loads = new int[problem.hospitalCount()];
        int trips = 0;
        for (Route route : routes) {
            count(loads, route, 1, problem.hospitalId(0));
            trips += route.tripCount();
        }
        return new PECTOSolution(problem, routes, loads, trips);
    }

    public PECTOProblem problem() {
        return problem;
    }

    // the number of routes, one per ambulance
    public int routeCount() {
        return routes.length;
    }

    public Route route(int ambulance) {
        return routes[ambulance];
    }

    // all routes, in ambulance order
    public List<Route> routes() {
        return Collections.unmodifiableList(Arrays.asList(routes));
    }

    // the number of trips of all routes (the number of patients, if the solution is feasible)
    public int tripCount() {
        return trips;
    }

    // the number of patients delivered to the hospital (by ID)
    public int load(int hospital) {
        return loads[hospital - problem.hospitalId(0)];
    }

    // this solution with the given routes replacing those of their ambulances
    public PECTOSolution withRoutes(Route... changed) {
        Route[] nextRoutes = routes.clone();
        int[] nextLoads = loads.clone();
        int nextTrips = trips;
        boolean[] replaced = new boolean[routes.length];
        for (Route route : changed) {
            int a = route.ambulance();
            if (route.problem() != problem)
                throw new IllegalArgumentException("route " + route + " belongs to another problem");
            if (replaced[a])
                throw new IllegalArgumentException("two routes for ambulance " + a);
            replaced[a] = true;
            count(nextLoads, routes[a], -1, problem.hospitalId(0));
            count(nextLoads, route, 1, problem.hospitalId(0));
            nextTrips += route.tripCount() - routes[a].tripCount();
            nextRoutes[a] = route;
        }
        return new PECTOSolution(problem, nextRoutes, nextLoads, nextTrips);
    }

    // adds sign times the patients the route delivers to each hospital to loads
    private static void count(int[] loads, Route route, int sign, int firstHospital) {
        int[] stops = route.stopArray();
        for (int k = 1; k < stops.length; k += 2)
            loads[stops[k] - firstHospital] += sign;
    }

    // The paper's encoding: every route as its ambulance ID followed by its stops, in ambulance order
    public int[] toSequence() {
        int[] sequence = new int[routes.length + 2 * trips];
        int next = 0;
        for (Route route : routes) {
            int[] stops = route.stopArray();
            sequence[next++] = route.ambulance();
            System.arraycopy(stops, 0, sequence, next, stops.length);
            next += stops.length;
        }
        return sequence;
    }

    @Override
    public PECTOSolution copy() {
        return this; // immutable
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof PECTOSolution))
            return false;
        PECTOSolution other = (PECTOSolution) o;
        return problem == other.problem && Arrays.equals(routes, other.routes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(routes);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PECTOSolution{");
        for (int a = 0; a < routes.length; a++)
            sb.append(a == 0 ? "" : " | ").append(routes[a]);
        return sb.append('}').toString();
    }
}
