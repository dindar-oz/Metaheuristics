package metaheuristic.hybridga;

import base.InitialSolutionGenerator;
import base.OptimizationProblem;
import metaheuristic.Params;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.operators.GreedyPECTOSG;
import util.random.RNG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Greedy solutions with a restricted candidate list, for the greedy share of the hybrid GA's population. The rule is
 * that of {@link GreedyPECTOSG}: the patients, most urgent first (ties by ID), are each delivered to the nearest
 * hospital with a free bed, but by an ambulance drawn uniformly among the {@code candidates} whose routes currently
 * end nearest to them. With one candidate this is {@link GreedyPECTOSG}, which gives the same solution every time;
 * the research GA filled a quarter of its population and every third set of migrants with greedy solutions, which
 * would then all be copies.
 */
public final class RandomizedGreedyPECTOSG implements InitialSolutionGenerator<PECTOSolution> {

    public static final int DEFAULT_CANDIDATES = 3;

    private final PECTOProblem problem;
    private final int candidates;
    private final int[] order;      // the patients, most urgent first

    public RandomizedGreedyPECTOSG(PECTOProblem problem, int candidates) {
        this.problem = problem;
        this.candidates = Params.positive("candidates", candidates);
        List<Integer> patients = new ArrayList<>();
        for (int k = 0; k < problem.patientCount(); k++)
            patients.add(problem.patientId(k));
        patients.sort(Comparator.comparingDouble(problem::urgencyWeight).thenComparingInt(Integer::intValue));
        order = patients.stream().mapToInt(Integer::intValue).toArray();
    }

    public RandomizedGreedyPECTOSG(PECTOProblem problem) {
        this(problem, DEFAULT_CANDIDATES);
    }

    @Override
    public PECTOSolution generate(OptimizationProblem<PECTOSolution> ignored, RNG rng) {
        int ambulances = problem.ambulanceCount();
        int shortlist = Math.min(candidates, ambulances);
        int[] end = new int[ambulances];                    // where each route ends now
        int[][] stops = new int[ambulances][2 * Math.max(1, order.length / ambulances)];
        int[] filled = new int[ambulances];
        for (int a = 0; a < ambulances; a++)
            end[a] = a;
        int[] room = new int[problem.hospitalCount()];
        for (int k = 0; k < room.length; k++)
            room[k] = problem.capacity(problem.hospitalId(k));

        int[] nearest = new int[shortlist];
        double[] distance = new double[shortlist];
        for (int patient : order) {
            // the shortlist nearest route ends, by insertion into a sorted array (ties: the lowest ID first)
            int found = 0;
            for (int a = 0; a < ambulances; a++) {
                double d = problem.distance(end[a], patient);
                if (found == shortlist && d >= distance[found - 1])
                    continue;
                int i = found < shortlist ? found++ : found - 1;
                while (i > 0 && distance[i - 1] > d) {
                    nearest[i] = nearest[i - 1];
                    distance[i] = distance[i - 1];
                    i--;
                }
                nearest[i] = a;
                distance[i] = d;
            }
            int ambulance = nearest[rng.randInt(found)];
            int hospital = nearestHospital(patient, room);
            room[problem.index(hospital)]--;
            if (filled[ambulance] == stops[ambulance].length)
                stops[ambulance] = Arrays.copyOf(stops[ambulance], 2 * stops[ambulance].length);
            stops[ambulance][filled[ambulance]++] = patient;
            stops[ambulance][filled[ambulance]++] = hospital;
            end[ambulance] = hospital;
        }
        for (int a = 0; a < ambulances; a++)
            stops[a] = Arrays.copyOf(stops[a], filled[a]);
        return problem.solution(stops);
    }

    // the nearest hospital with a free bed; there is always one, the hospitals can take all patients
    private int nearestHospital(int patient, int[] room) {
        int nearest = -1;
        double best = Double.POSITIVE_INFINITY;
        for (int k = 0; k < room.length; k++) {
            double d = problem.distance(patient, problem.hospitalId(k));
            if (room[k] > 0 && d < best) {
                best = d;
                nearest = problem.hospitalId(k);
            }
        }
        return nearest;
    }

    @Override
    public String toString() {
        return "RandomizedGreedyPECTOSG(" + candidates + ")";
    }
}
