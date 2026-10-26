package alg.ga.pecto;

import alg.ga.CrossOverOperator;
import problem.OptimizationProblem;
import problem.ProblemModel;
import problem.SimpleOptimizationProblem;
import problem.pecto.PECTOMinimumTimeOF;
import problem.pecto.PECTOModel2;
import problem.pecto.PECTORandomISG;
import representation.IntegerPermutation;
import representation.PectoSolution;
import representation.Representation;
import representation.Solution;

import java.util.ArrayList;
import java.util.List;

import static problem.pecto.PECTOModel2.ParticipantType;

/**
 * Cycle Crossover (CX) implementation for PECTO problem.
 */

public class PECTOCrossOverAEX implements CrossOverOperator {
    PECTOModel2 pectoModel;
    SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> pectoProblem;
    List<Integer> p1rep;
    List<Integer> p2rep;
    List<Integer> offspring1;
    List<Integer> offspring2;
    int[] patients1;
    int[] patients2;
    int[] updatedPatients1;
    int[] updatedPatients2;
    boolean[] visited1;
    boolean[] visited2;
    int[] positionInPatients1;
    int[] positionInPatients2;

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
        visited1 = new boolean[pectoModel.getNumPatients()];
        visited2 = new boolean[pectoModel.getNumPatients()];

        extractPatients();
        positionInPatients1 = computePositions(patients1);
        positionInPatients2 = computePositions(patients2);
        updatedPatients1 = performAlternatingEdgesCrossOver(patients1, patients2, positionInPatients1, positionInPatients2);
        updatedPatients2 = performAlternatingEdgesCrossOver(patients2, patients1, positionInPatients2, positionInPatients1);
        reinsertPatients();
        return listOffsprings();
    }

    private int[] computePositions(int[] patients) {
        int[] list = new int[pectoModel.getNumPatients()];

        for (int i = 0; i < pectoModel.getNumPatients(); i++) {
            list[patients[i] - pectoModel.getNumAmbulances()] = i;
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
        placePatients(p1rep, offspring1, updatedPatients1);
        placePatients(p2rep, offspring2, updatedPatients2);
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

    private int[] performAlternatingEdgesCrossOver(int[] patientsX, int[] patientsY, int[] positionInPatientsX, int[] positionInPatientsY) {
        int[] child = new int[pectoModel.getNumPatients()];
        boolean[] used = new boolean[pectoModel.getNumPatients()];
        int index = 0;
        int counter = 0;

        child[counter] = patientsX[index];
        used[patientsX[index] - pectoModel.getNumAmbulances()] = true;
        index++;
        counter++;

        while (counter < pectoModel.getNumPatients()) {
            while (used[patientsX[index] - pectoModel.getNumAmbulances()]) {
                index = (index + 1) % pectoModel.getNumPatients();
            }

            child[counter] = patientsX[index];
            used[patientsX[index] - pectoModel.getNumAmbulances()] = true;
            counter++;

            index = positionInPatientsY[child[counter - 1] - pectoModel.getNumAmbulances()];

            while (used[patientsY[index] - pectoModel.getNumAmbulances()]) {
                index = (index + 1) % pectoModel.getNumPatients();
            }

            child[counter] = patientsY[index];
            used[patientsY[index] - pectoModel.getNumAmbulances()] = true;
            counter++;

            index = positionInPatientsX[child[counter - 1] - pectoModel.getNumAmbulances()];
        }

        return child;
    }

    private void extractPatients() {
        int x = 0;
        int y = 0;

        for (Integer integer : p1rep) {
            if (pectoModel.getParticipantType(integer).equals(ParticipantType.PATIENT)) {
                patients1[x++] = integer;
            }
        }

        for (Integer integer : p2rep) {
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
        PECTOModel2 pectoModel = new PECTOModel2(200, 7290, 64);
        SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> problem =
                new SimpleOptimizationProblem<>(pectoModel, new PECTOMinimumTimeOF());
        PectoSolution pectoSolution1 = ((PectoSolution) new PECTORandomISG(pectoModel).generate(problem));
        PectoSolution pectoSolution2 = ((PectoSolution) new PECTORandomISG(pectoModel).generate(problem));

        PECTOCrossOverAEX pectoCrossOverCX = new PECTOCrossOverAEX();
        System.out.println("S1: " + pectoSolution1);
        System.out.println("S2: " + pectoSolution2);
        List<Solution> offsprings = pectoCrossOverCX.apply(problem, pectoSolution1, pectoSolution2);
        int x = 1;
        for (Solution s : offsprings) {
            System.out.println("O" + x++ + ": " + s);
        }
    }
}

