package alg.ga.pecto;

import alg.ga.CrossOverOperator;
import alg.greedy.pecto.PECTOGreedyHeuristic;
import alg.greedy.pecto.PECTONearestParticipantEvaluator;
import problem.OptimizationProblem;
import problem.ProblemModel;
import problem.SimpleOptimizationProblem;
import problem.pecto.*;
import representation.*;

import java.util.ArrayList;
import java.util.List;

import static problem.pecto.PECTOModel2.*;
import static utils.RandUtils.generateRandomInt;

/**
 * Route-Aligned Ordered Crossover (OX-2) for PECTO.
 * Selects a range of routes and preserves them in the offspring.
 */
public class PECTOCrossOverOX implements CrossOverOperator {
    private PECTOModel2 pectoModel;
    private SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> pectoProblem;
    private List<Integer> p1rep;
    private List<Integer> p2rep;
    private List<Integer> offspring1;
    private List<Integer> offspring2;
    private int[] patients1;
    private int[] patients2;

    @Override
    public <R extends Representation, PM extends ProblemModel<R>> List<Solution> apply(OptimizationProblem<PM, R> problem, Solution parent1, Solution parent2) {
        pectoModel = (PECTOModel2) problem.model();
        pectoProblem = (SimpleOptimizationProblem<PECTOModel2, IntegerPermutation>) problem;

        PectoSolution ps1 = (PectoSolution) parent1;
        PectoSolution ps2 = (PectoSolution) parent2;

        p1rep = ps1.toList();
        p2rep = ps2.toList();
        offspring1 = new ArrayList<>(p1rep.size());
        offspring2 = new ArrayList<>(p2rep.size());
        patients1 = new int[pectoModel.getNumPatients()];
        patients2 = new int[pectoModel.getNumPatients()];

        extractPatients();
        performRouteAlignedOX(ps1, ps2);
        reinsertPatients();
        return listOffsprings();
    }

    private void performRouteAlignedOX(PectoSolution ps1, PectoSolution ps2) {
        int numPatients = pectoModel.getNumPatients();
        int numAmbs = pectoModel.getNumAmbulances();

        int[] rStarts1 = getRoutePatientStarts(ps1);
        int[] rStarts2 = getRoutePatientStarts(ps2);

        int r1 = generateRandomInt(numAmbs);
        int r2 = generateRandomInt(numAmbs);
        int rStart = Math.min(r1, r2);
        int rEnd = Math.max(r1, r2);

        int left1 = rStarts1[rStart];
        int right1 = rStarts1[rEnd + 1] - 1;

        System.out.println("L1: " + left1);
        System.out.println("R1: " + right1);

        int left2 = rStarts2[rStart];
        int right2 = rStarts2[rEnd + 1] - 1;

        System.out.println("L2: " + left2);
        System.out.println("R2: " + right2);

        // --- Child 1 (Segment from P1, Fill from P2) ---
        int[] child1 = new int[numPatients];
        boolean[] used1 = new boolean[numPatients];
        int ambOffset = pectoModel.getNumAmbulances();

        // Copy segment from P1
        for (int i = left1; i <= right1; i++) {
            int p = patients1[i];
            child1[i] = p;
            used1[p - ambOffset] = true;
        }

        // Fill remaining from P2 in cyclic order
        int writeIdx1 = (right1 + 1) % numPatients;
        for (int k = 0; k < numPatients; k++) {
            int p = patients2[(right2 + 1 + k) % numPatients];
            if (!used1[p - ambOffset]) {
                child1[writeIdx1] = p;
                used1[p - ambOffset] = true;
                writeIdx1 = (writeIdx1 + 1) % numPatients;
            }
        }

        // --- Child 2 (Segment from P2, Fill from P1) ---
        int[] child2 = new int[numPatients];
        boolean[] used2 = new boolean[numPatients];

        // Copy segment from P2
        for (int i = left2; i <= right2; i++) {
            int p = patients2[i];
            child2[i] = p;
            used2[p - ambOffset] = true;
        }

        // Fill remaining from P1 in cyclic order
        int writeIdx2 = (right2 + 1) % numPatients;
        for (int k = 0; k < numPatients; k++) {
            int p = patients1[(right1 + 1 + k) % numPatients];
            if (!used2[p - ambOffset]) {
                child2[writeIdx2] = p;
                used2[p - ambOffset] = true;
                writeIdx2 = (writeIdx2 + 1) % numPatients;
            }
        }

        patients1 = child1;
        patients2 = child2;
    }

    private int[] getRoutePatientStarts(PectoSolution solution) {
        List<List<Integer>> routes = solution.getRoutes();
        int[] starts = new int[routes.size() + 1];
        int current = 0;
        for (int i = 0; i < routes.size(); i++) {
            starts[i] = current;
            // Route structure: [Ambulance, P, H, P, H...]
            // Number of patients = (size - 1) / 2
            current += (routes.get(i).size() - 1) / 2;
        }
        starts[routes.size()] = current;
        return starts;
    }

    private void extractPatients() {
        int x = 0;
        int y = 0;
        for (int id : p1rep) {
            if (pectoModel.getParticipantType(id) == ParticipantType.PATIENT) {
                patients1[x++] = id;
            }
        }
        for (int id : p2rep) {
            if (pectoModel.getParticipantType(id) == ParticipantType.PATIENT) {
                patients2[y++] = id;
            }
        }
    }

    private void reinsertPatients() {
        placePatients(p1rep, offspring1, patients1);
        placePatients(p2rep, offspring2, patients2);
    }

    private void placePatients(List<Integer> structure, List<Integer> result, int[] patientArray) {
        int x = 0;
        for (int id : structure) {
            if (pectoModel.getParticipantType(id) == ParticipantType.PATIENT) {
                result.add(patientArray[x++]);
            } else {
                result.add(id);
            }
        }
    }

    private List<Solution> listOffsprings() {
        List<Solution> offsprings = new ArrayList<>();
        offsprings.add(new PectoSolution(new IntegerPermutation(offspring1), pectoProblem));
        offsprings.add(new PectoSolution(new IntegerPermutation(offspring2), pectoProblem));
        return offsprings;
    }

    @Override
    public String toString() {
        return "OX2 ";
    }

    public static void main(String[] args) {
        PECTOModel2 pectoModel = new PECTOModel2(200, 5163, 64);
        SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> problem =
                new SimpleOptimizationProblem<>(pectoModel, new PECTOMinimumTimeOF());

        PECTOGreedyHeuristic pectoGreedyHeuristic = new PECTOGreedyHeuristic(new PECTONearestParticipantEvaluator(), null);
        PectoSolution p1 = (PectoSolution) pectoGreedyHeuristic.solve(problem.model(), problem);
        PectoSolution p2 = (PectoSolution) pectoGreedyHeuristic.solve(problem.model(), problem);

        PECTOCrossOverOX ox2 = new PECTOCrossOverOX();
        System.out.println("Parent 1: " + p1);
        System.out.println("Parent 2: " + p2);
        
        List<Solution> offsprings = ox2.apply(problem, p1, p2);
        System.out.println("Offspring 1: " + offsprings.get(0));
        System.out.println("Offspring 2: " + offsprings.get(1));
    }
}
