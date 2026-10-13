package problems.pecto.operators;

import base.InitialSolutionGenerator;
import base.OptimizationProblem;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import util.random.RNG;

/**
 * Random feasible solutions, without regard to distances or urgencies: the patients, in random order, are each given
 * to a random ambulance, which delivers them to a random hospital that still has a free bed.
 */
public final class RandomPECTOSG implements InitialSolutionGenerator<PECTOSolution> {

    private final PECTOProblem problem;

    public RandomPECTOSG(PECTOProblem problem) {
        this.problem = problem;
    }

    @Override
    public PECTOSolution generate(OptimizationProblem<PECTOSolution> ignored, RNG rng) {
        int patients = problem.patientCount(), ambulances = problem.ambulanceCount();

        // which ambulance transports the i-th patient of a random order
        int[] order = new int[patients];
        for (int i = 0; i < patients; i++) {
            int j = rng.randInt(i + 1);                 // inside-out Fisher-Yates
            order[i] = order[j];
            order[j] = problem.patientId(i);
        }
        int[] ambulance = new int[patients];
        int[] trips = new int[ambulances];
        for (int i = 0; i < patients; i++)
            trips[ambulance[i] = rng.randInt(ambulances)]++;

        // the hospitals with free beds are open[0 .. openCount)
        int[] room = new int[problem.hospitalCount()];
        int[] open = new int[room.length];
        int openCount = 0;
        for (int k = 0; k < room.length; k++)
            if ((room[k] = problem.capacity(problem.hospitalId(k))) > 0)
                open[openCount++] = k;

        int[][] stops = new int[ambulances][];
        for (int a = 0; a < ambulances; a++)
            stops[a] = new int[2 * trips[a]];
        int[] filled = new int[ambulances];
        for (int i = 0; i < patients; i++) {
            int j = rng.randInt(openCount), h = open[j];
            if (--room[h] == 0)
                open[j] = open[--openCount];
            int[] route = stops[ambulance[i]];
            route[filled[ambulance[i]]++] = order[i];
            route[filled[ambulance[i]]++] = problem.hospitalId(h);
        }
        return problem.solution(stops);
    }

    @Override
    public String toString() {
        return "RandomPECTOSG";
    }
}
