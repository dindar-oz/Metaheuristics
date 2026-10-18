package problems.pecto.operators;

import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The best hospitals for the trips of a solution. Every trip keeps its ambulance and its place in the route; only the
 * hospitals change, to those that minimize the cost within the capacities.
 * <p>
 * The hospital of a trip changes only two legs: the delivery of its patient and the pickup of the next patient of the
 * route (Eq. 8). Each trip t therefore costs c(t, h) at hospital h whatever the other trips do, and choosing the
 * hospitals is a transportation problem: trips to hospitals of limited capacity. It is solved exactly by cancelling
 * negative cycles in a graph whose nodes are the hospitals and the free beds: the edge h -> h' moves the trip of h that
 * gains most from going to h', h -> beds takes a free bed of h, beds -> h frees one. When no cycle has a negative
 * cost, no set of trip moves improves the solution, which is then optimal for its routes.
 * <p>
 * After a cycle is cancelled only the edges out of its hospitals change, and Bellman-Ford stops at the first cycle of
 * its predecessor graph, so a cancellation costs about (cycle length x trips + passes x hospitals^2).
 */
public final class OptimalHospitals {

    private static final double EPSILON = 1e-9;     // smaller improvements are taken for rounding errors

    private OptimalHospitals() {
    }

    /**
     * The solution with the same routes and the best hospitals for them; the solution itself if its hospitals are
     * already optimal. Throws IllegalArgumentException if a hospital receives more patients than its capacity.
     */
    public static PECTOSolution of(PECTOSolution solution) {
        PECTOProblem problem = solution.problem();
        for (int k = 0; k < problem.hospitalCount(); k++)
            if (problem.room(solution, problem.hospitalId(k)) < 0)
                throw new IllegalArgumentException("hospital " + problem.hospitalId(k) + " is over capacity");
        int[] original = hospitalIndices(solution);
        int[] at = new Assignment(solution, original).optimize();
        if (Arrays.equals(at, original))
            return solution;

        List<Route> changed = new ArrayList<>();
        int t = 0;
        for (Route route : solution.routes()) {
            int[] stops = route.stops();
            boolean differs = false;
            for (int k = 0; k < route.tripCount(); k++, t++)
                if (at[t] != original[t]) {
                    stops[2 * k + 1] = problem.hospitalId(at[t]);
                    differs = true;
                }
            if (differs)
                changed.add(problem.route(route.ambulance(), stops));
        }
        return solution.withRoutes(changed.toArray(new Route[0]));
    }

    // the hospital index of every trip, routes in ambulance order
    private static int[] hospitalIndices(PECTOSolution solution) {
        int[] at = new int[solution.tripCount()];
        int t = 0;
        for (Route route : solution.routes())
            for (int k = 0; k < route.tripCount(); k++)
                at[t++] = solution.problem().index(route.hospital(k));
        return at;
    }

    // The trips' hospitals and the exchange graph between them; node `beds` stands for the free beds
    private static final class Assignment {

        private final int hospitals, beds, nodes;
        private final double[][] cost;      // c(t, h)
        private final int[] at;             // the hospital index of every trip
        private final int[][] members;      // the trips of every hospital: the first size[h] entries
        private final int[] size;
        private final int[] slot;           // of every trip in its hospital's members
        private final int[] capacity;
        private final double[][] edge;      // infinite where there is no edge
        private final int[][] moved;        // the trip that edge h -> h' moves
        private final double[] distance;
        private final int[] previous;
        private final int[] mark;

        Assignment(PECTOSolution solution, int[] hospitalIndices) {
            PECTOProblem problem = solution.problem();
            hospitals = problem.hospitalCount();
            beds = hospitals;
            nodes = hospitals + 1;
            int trips = solution.tripCount();
            cost = new double[trips][hospitals];
            int t = 0;
            for (Route route : solution.routes())
                for (int k = 0; k < route.tripCount(); k++, t++) {
                    int patient = route.patient(k), next = k + 1 < route.tripCount() ? route.patient(k + 1) : -1;
                    for (int h = 0; h < hospitals; h++) {
                        int hospital = problem.hospitalId(h);
                        cost[t][h] = problem.urgencyWeight(patient) * problem.distance(patient, hospital)
                                + (next < 0 ? 0 : problem.urgencyWeight(next) * problem.distance(hospital, next));
                    }
                }
            at = hospitalIndices.clone();
            capacity = new int[hospitals];
            members = new int[hospitals][];
            size = new int[hospitals];
            slot = new int[trips];
            for (int h = 0; h < hospitals; h++) {
                capacity[h] = problem.capacity(problem.hospitalId(h));
                members[h] = new int[capacity[h]];
            }
            for (t = 0; t < trips; t++) {
                slot[t] = size[at[t]];
                members[at[t]][size[at[t]]++] = t;
            }
            edge = new double[nodes][nodes];
            moved = new int[nodes][nodes];
            for (double[] row : edge)
                Arrays.fill(row, Double.POSITIVE_INFINITY);
            for (int h = 0; h < hospitals; h++) {
                edge[beds][h] = 0;
                row(h);
            }
            distance = new double[nodes];
            previous = new int[nodes];
            mark = new int[nodes];
        }

        int[] optimize() {
            int[] cycle;
            int[] trips = new int[nodes], targets = new int[nodes];
            while ((cycle = negativeCycle()) != null) {
                // every trip leaves before any arrives, so that no hospital holds more trips than beds
                int count = 0;
                for (int k = 0; k < cycle.length; k++) {
                    int from = cycle[k], to = cycle[(k + 1) % cycle.length];
                    if (from != beds && to != beds) {
                        trips[count] = moved[from][to];
                        targets[count++] = to;
                    }
                }
                for (int i = 0; i < count; i++)
                    remove(trips[i]);
                for (int i = 0; i < count; i++)
                    add(trips[i], targets[i]);
                for (int h : cycle)
                    if (h != beds)
                        row(h);
            }
            return at;
        }

        private void remove(int t) {
            int h = at[t], last = members[h][--size[h]];
            members[h][slot[t]] = last;
            slot[last] = slot[t];
        }

        private void add(int t, int h) {
            slot[t] = size[h];
            members[h][size[h]++] = t;
            at[t] = h;
        }

        // the edges out of hospital h
        private void row(int h) {
            double[] out = edge[h];
            Arrays.fill(out, Double.POSITIVE_INFINITY);
            for (int i = 0; i < size[h]; i++) {
                int t = members[h][i];
                double[] c = cost[t];
                for (int to = 0; to < hospitals; to++) {
                    double gain = c[to] - c[h];
                    if (to != h && gain < out[to]) {
                        out[to] = gain;
                        moved[h][to] = t;
                    }
                }
            }
            if (size[h] < capacity[h])
                out[beds] = 0;
        }

        // Bellman-Ford from a virtual source joined to every node at cost 0, stopping at the first cycle of the
        // predecessor graph; returns its nodes in edge order, or null if no cycle costs below -EPSILON
        private int[] negativeCycle() {
            Arrays.fill(distance, 0);
            Arrays.fill(previous, -1);
            for (int pass = 0; pass < nodes; pass++) {
                boolean relaxed = false;
                for (int u = 0; u < nodes; u++)
                    for (int v = 0; v < nodes; v++)
                        if (distance[u] + edge[u][v] < distance[v] - EPSILON) {
                            distance[v] = distance[u] + edge[u][v];
                            previous[v] = u;
                            relaxed = true;
                        }
                if (!relaxed)
                    return null;
                int[] cycle = predecessorCycle();
                if (cycle != null)
                    return cycle;
            }
            return null;
        }

        // a cycle of the predecessor graph costing below -EPSILON, or null
        private int[] predecessorCycle() {
            Arrays.fill(mark, -1);
            for (int start = 0; start < nodes; start++) {
                int v = start;
                while (v >= 0 && mark[v] < 0) {
                    mark[v] = start;
                    v = previous[v];
                }
                if (v < 0 || mark[v] != start)
                    continue;       // reached the source or a walk already followed
                List<Integer> reversed = new ArrayList<>();
                int u = v;
                do {
                    reversed.add(u);
                    u = previous[u];
                } while (u != v);
                int[] cycle = new int[reversed.size()];
                for (int k = 0; k < cycle.length; k++)
                    cycle[k] = reversed.get(cycle.length - 1 - k);
                double total = 0;
                for (int k = 0; k < cycle.length; k++)
                    total += edge[cycle[k]][cycle[(k + 1) % cycle.length]];
                if (total < -EPSILON)
                    return cycle;
            }
            return null;
        }
    }
}
