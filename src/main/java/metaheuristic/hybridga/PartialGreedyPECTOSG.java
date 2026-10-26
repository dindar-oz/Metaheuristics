package metaheuristic.hybridga;

import base.InitialSolutionGenerator;
import base.OptimizationProblem;
import metaheuristic.Params;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Route;
import problems.pecto.operators.GreedyPECTOSG;
import util.random.RNG;

import java.util.ArrayList;
import java.util.List;

/**
 * The "hybrid" initial solutions of the research hybrid GA (PECTOHybridISG2): every ambulance keeps its route of the
 * greedy solution ({@link GreedyPECTOSG}) with probability {@code keepProbability}, and starts empty otherwise. The
 * patients of the dropped routes, in random order, are then dealt round-robin to the ambulances whose routes were
 * dropped, each delivered to a random hospital with a free bed. The solutions are feasible, mostly greedy and partly
 * random.
 * <p>
 * The greedy solution is computed once, by the constructor; the generator is immutable and can be shared.
 */
public final class PartialGreedyPECTOSG implements InitialSolutionGenerator<PECTOSolution> {

    public static final double DEFAULT_KEEP_PROBABILITY = 0.9;

    private final PECTOProblem problem;
    private final PECTOSolution greedy;
    private final double keepProbability;

    public PartialGreedyPECTOSG(PECTOProblem problem, double keepProbability) {
        this.problem = problem;
        this.greedy = GreedyPECTOSG.complete(problem.emptySolution());
        this.keepProbability = Params.probability("keepProbability", keepProbability);
    }

    public PartialGreedyPECTOSG(PECTOProblem problem) {
        this(problem, DEFAULT_KEEP_PROBABILITY);
    }

    @Override
    public PECTOSolution generate(OptimizationProblem<PECTOSolution> ignored, RNG rng) {
        List<Integer> dropped = new ArrayList<>();
        List<Integer> orphans = new ArrayList<>();      // the patients of the dropped routes
        for (int a = 0; a < problem.ambulanceCount(); a++)
            if (!rng.rollDice(keepProbability)) {
                dropped.add(a);
                Route route = greedy.route(a);
                for (int t = 0; t < route.tripCount(); t++)
                    orphans.add(route.patient(t));
            }
        if (orphans.isEmpty())
            return greedy;

        // the hospitals with free beds once the dropped routes are gone are open[0 .. openCount)
        int[] room = new int[problem.hospitalCount()];
        for (int k = 0; k < room.length; k++)
            room[k] = problem.room(greedy, problem.hospitalId(k));
        for (int a : dropped) {
            Route route = greedy.route(a);
            for (int t = 0; t < route.tripCount(); t++)
                room[problem.index(route.hospital(t))]++;
        }
        int[] open = new int[room.length];
        int openCount = 0;
        for (int k = 0; k < room.length; k++)
            if (room[k] > 0)
                open[openCount++] = k;

        for (int i = orphans.size() - 1; i > 0; i--) {   // Fisher-Yates
            int j = rng.randInt(i + 1);
            orphans.set(j, orphans.set(i, orphans.get(j)));
        }
        int[][] stops = new int[dropped.size()][];
        for (int d = 0; d < stops.length; d++)
            stops[d] = new int[2 * ((orphans.size() - d + stops.length - 1) / stops.length)];
        for (int i = 0; i < orphans.size(); i++) {
            int j = rng.randInt(openCount), h = open[j];
            if (--room[h] == 0)
                open[j] = open[--openCount];
            int d = i % stops.length, trip = i / stops.length;
            stops[d][2 * trip] = orphans.get(i);
            stops[d][2 * trip + 1] = problem.hospitalId(h);
        }

        Route[] changed = new Route[dropped.size()];
        for (int d = 0; d < changed.length; d++)
            changed[d] = problem.route(dropped.get(d), stops[d]);
        return greedy.withRoutes(changed);
    }

    @Override
    public String toString() {
        return "PartialGreedyPECTOSG(" + keepProbability + ")";
    }
}
