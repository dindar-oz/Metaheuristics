package alg.ga.pecto;

import alg.ga.MutationOperation;
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

import static utils.RandUtils.generateRandomInt;

/**
 * Within a single route, picks a random patient and swaps it
 * with the first less urgent patient found earlier in the serving order.
 */

public class PECTOMutationSwap2 implements MutationOperation {
    @Override
    public <R extends Representation, PM extends ProblemModel<R>> void apply(OptimizationProblem<PM, R> problem, Solution s) {
        PECTOModel2 pectoModel = (PECTOModel2) problem.model();
        PectoSolution pectoSolution = (PectoSolution) s;

        int route1 = generateRandomInt(pectoModel.getNumAmbulances());
        List<Integer> ambulance = pectoSolution.getRoutes().get(route1);

        while(ambulance.size() <= 3) {
            route1 = (route1 + 1) % pectoModel.getNumAmbulances();
            ambulance = pectoSolution.getRoutes().get(route1);
        }

        int index1 = 3 + generateRandomInt(ambulance.size() - 3);

        if ((index1 % 2) == 0) {
            index1--;
        }

        int index2 = index1 - 2;

        int solutionIndex1 = findSolutionIndex(pectoSolution, route1, index1);
        int solutionIndex2 = findSolutionIndex(pectoSolution, route1, index2);

        while (pectoModel.getPatientUrgencyMultiplier(((IntegerPermutation)(pectoSolution.getRepresentation())).get(solutionIndex1))
                > pectoModel.getPatientUrgencyMultiplier(((IntegerPermutation)(pectoSolution.getRepresentation())).get(solutionIndex2))) {
            index2 -= 2;

            if (index2 <= 0) {
                index2 = index1;
                solutionIndex2 = solutionIndex1;
                break;
            }

            solutionIndex2 = findSolutionIndex(pectoSolution, route1, index2);
        }

        int participant1 = ambulance.get(index1);
        int participant2 = ambulance.get(index2);

        ambulance.set(index1, participant2);
        ambulance.set(index2, participant1);

        List<Integer> initialList1 = extractSegment(pectoSolution, solutionIndex1, solutionIndex1);
        List<Integer> finalList1 = extractSegment(pectoSolution, solutionIndex1, solutionIndex2);
        List<Integer> initialList2 = extractSegment(pectoSolution, solutionIndex2, solutionIndex2);
        List<Integer> finalList2 = extractSegment(pectoSolution, solutionIndex2, solutionIndex1);

        double initialValue1 = problem.getObjective().value(problem.model(), (R) new IntegerPermutation(initialList1));
        double finalValue1   = problem.getObjective().value(problem.model(), (R) new IntegerPermutation(finalList1));
        double initialValue2 = problem.getObjective().value(problem.model(), (R) new IntegerPermutation(initialList2));
        double finalValue2   = problem.getObjective().value(problem.model(), (R) new IntegerPermutation(finalList2));

        ((IntegerPermutation)(pectoSolution.getRepresentation())).swap(solutionIndex1, solutionIndex2);
        double delta1 = finalValue1 - initialValue1;
        pectoSolution.updateRouteCost(route1, delta1);
        double delta2 = finalValue2 - initialValue2;
        pectoSolution.updateRouteCost(route1, delta2);
    }

    private List<Integer> extractSegment(Solution s, int index1, int index2) {
        List<Integer> list = new ArrayList<>();

        if ((index1 - 1) >= 0) {
            list.add(((IntegerPermutation)(s.getRepresentation())).get(index1 - 1));
        }

        list.add(((IntegerPermutation)(s.getRepresentation())).get(index2));

        if ((index1 + 1) < ((PectoSolution) s).size()) {
            list.add(((IntegerPermutation)(s.getRepresentation())).get(index1 + 1));
        }

        return list;
    }

    private int findSolutionIndex(PectoSolution pectoSolution, int route, int index) {
        int solutionIndex = index;

        for (int i = 0; i < route; i++) {
            solutionIndex += pectoSolution.getRoutes().get(i).size();
        }

        return solutionIndex;
    }

    @Override
    public String toString() {
        return "SWAP";
    }

    public static void main(String[] args) {
        PECTOModel2 pectoModel = new PECTOModel2(200, 5163, 64);
        SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> problem =
                new SimpleOptimizationProblem<>(pectoModel, new PECTOMinimumTimeOF());

        PECTORandomISG isg = new PECTORandomISG(pectoModel);
        PectoSolution pectoSolution2 = (PectoSolution)isg.generate(problem);
        PECTOMutationSwap2 p1 = new PECTOMutationSwap2();
        System.out.println(pectoSolution2);
        p1.apply(problem, pectoSolution2);
        System.out.println(pectoSolution2);

    }
}
