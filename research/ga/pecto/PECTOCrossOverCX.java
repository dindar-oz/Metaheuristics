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
 * Cycle Crossover (CX) implementation for PECTO problem.
 */

public class PECTOCrossOverCX implements CrossOverOperator {
    PECTOModel2 pectoModel;
    SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> pectoProblem;
    List<Integer> p1rep;
    List<Integer> p2rep;
    List<Integer> offspring1;
    List<Integer> offspring2;
    int[] patients1;
    int[] patients2;
    boolean[] cycle;
    int[] positionInPatients1;

    @Override
    public <R extends Representation, PM extends ProblemModel<R>> List<Solution> apply(OptimizationProblem<PM, R> problem, Solution parent1, Solution parent2) {
        pectoModel = (PECTOModel2) problem.model();
        pectoProblem = (SimpleOptimizationProblem<PECTOModel2, IntegerPermutation>) problem;

        p1rep = ((PectoSolution) parent1).toList();
        p2rep = ((PectoSolution) parent2).toList();
        offspring1 = new ArrayList<>(p1rep.size());
        offspring2 = new ArrayList<>(p2rep.size());
        patients1 = new int[pectoModel.getNumPatients()];
        patients2 = new int[pectoModel.getNumPatients()];
        cycle = new boolean[pectoModel.getNumPatients()];

        extractPatients();
        positionInPatients1 = computePositions();
        performCycleCrossOver();
        reinsertPatients();
        return listOffsprings();
    }

    private int[] computePositions() {
        int[] list = new int[pectoModel.getNumPatients()];

        for (int i = 0; i < pectoModel.getNumPatients(); i++) {
            list[patients1[i] - pectoModel.getNumAmbulances()] = i;
        }

        return list;
    }

    private List<Solution> listOffsprings() {
        List<Solution> offsprings = new ArrayList<>();
        IntegerPermutation o1 = new IntegerPermutation(offspring1);
        IntegerPermutation o2 = new IntegerPermutation(offspring2);

        offsprings.add(new PectoSolution(o1, pectoProblem));
        offsprings.add(new PectoSolution(o2, pectoProblem));
        return offsprings;
    }

    private void reinsertPatients() {
        placePatients(p1rep, offspring1, patients1);
        placePatients(p2rep, offspring2, patients2);
    }

    private void placePatients(List<Integer> rep, List<Integer> offspring, int[] patients) {
        int x = 0;

        for (int p1 = 0; p1 < rep.size(); p1++) {
            if (pectoModel.getParticipantType(rep.get(p1)).equals(ParticipantType.PATIENT)) {
                offspring.add(p1, patients[x++]);
            } else {
                offspring.add(p1, rep.get(p1));
            }
        }
    }

    private void performCycleCrossOver() {
        int startIndex = generateRandomInt(pectoModel.getNumPatients());
        int startValue = patients1[startIndex];
        cycle[startValue - pectoModel.getNumAmbulances()] = true;

        int nextValue = patients2[startIndex];

        while (!cycle[nextValue - pectoModel.getNumAmbulances()]) {
            cycle[nextValue - pectoModel.getNumAmbulances()] = true;
            startIndex = positionInPatients1[nextValue - pectoModel.getNumAmbulances()];
            nextValue = patients2[startIndex];
        }

        for (int i = 0; i < pectoModel.getNumPatients(); i++) {
            if (!cycle[patients1[i] - pectoModel.getNumAmbulances()]) {
                int tmp = patients1[i];
                patients1[i] = patients2[i];
                patients2[i] = tmp;
            }
        }
    }

    private void extractPatients() {
        int x = 0;
        int y = 0;

        for (int integer : p1rep) {
            if (pectoModel.getParticipantType(integer).equals(ParticipantType.PATIENT)) {
                patients1[x++] = integer;
            }
        }

        for (int integer : p2rep) {
            if (pectoModel.getParticipantType(integer).equals(ParticipantType.PATIENT)) {
                patients2[y++] = integer;
            }
        }
    }

    @Override
    public String toString() {
        return "CX-XOver ";
    }

    public static void main(String[] args) {
        PECTOModel2 pectoModel = new PECTOModel2(200, 5163, 64);
        SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> problem =
                new SimpleOptimizationProblem<>(pectoModel, new PECTOMinimumTimeOF());
        PECTOGreedyHeuristic heuristic = new PECTOGreedyHeuristic(new PECTONearestParticipantEvaluator(), null);
        PectoSolution pectoSolution1 = (PectoSolution) heuristic.solve(pectoModel, problem);
        PectoSolution pectoSolution2 = (PectoSolution) heuristic.solve(pectoModel, problem);

        PECTOCrossOverCX pectoCrossOverCX = new PECTOCrossOverCX();
        System.out.println("S1: " + pectoSolution1);
        System.out.println("S2: " + pectoSolution2);
        List<Solution> offsprings = pectoCrossOverCX.apply(problem, pectoSolution1, pectoSolution2);
        int x = 1;
        for (Solution s : offsprings) {
            System.out.println("O" + x++ + ": " + s);
        }
    }
}

