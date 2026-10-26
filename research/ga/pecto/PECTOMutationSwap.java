package alg.ga.pecto;

import alg.ga.MutationOperation;
import problem.OptimizationProblem;
import problem.ProblemModel;
import problem.SimpleOptimizationProblem;
import problem.pecto.*;
import representation.*;

import java.util.ArrayList;
import java.util.List;

import static utils.RandUtils.generateRandomInt;

/**
 * Picks one random participant from two different routes
 * and swaps them, ensuring both are the same type (PATIENT or HOSPITAL).
 */

public class PECTOMutationSwap implements MutationOperation {
    @Override
    public <R extends Representation, PM extends ProblemModel<R>> void apply(OptimizationProblem<PM, R> problem, Solution s) {
        PECTOModel2 pectoModel = (PECTOModel2) problem.model();
        PectoSolution pectoSolution = (PectoSolution) s;

        int route1 = generateRandomInt(pectoModel.getNumAmbulances());
        int route2 = generateRandomInt(pectoModel.getNumAmbulances());

        List<Integer> ambulance1 = pectoSolution.getRoutes().get(route1);

        while(ambulance1.size() == 1) {
            route1 = (route1 + 1) % pectoModel.getNumAmbulances();
            ambulance1 = pectoSolution.getRoutes().get(route1);
        }

        List<Integer> ambulance2 = pectoSolution.getRoutes().get(route2);

        while(ambulance2.size() == 1) {
            route2 = (route2 + 1) % pectoModel.getNumAmbulances();
            ambulance2 = pectoSolution.getRoutes().get(route2);
        }

        int index1 = 1 + generateRandomInt(ambulance1.size() - 1);
        int index2 = 1 + generateRandomInt(ambulance2.size() - 1);

        int solutionIndex1 = findSolutionIndex(pectoSolution, route1, index1);
        int solutionIndex2 = findSolutionIndex(pectoSolution, route2, index2);

        while (!pectoModel.getParticipantType(((IntegerPermutation)(pectoSolution.getRepresentation())).get(solutionIndex1))
                .equals(pectoModel.getParticipantType(((IntegerPermutation)(pectoSolution.getRepresentation())).get(solutionIndex2)))) {
            index2 = 1 + generateRandomInt(ambulance2.size() - 1);
            solutionIndex2 = findSolutionIndex(pectoSolution, route2, index2);
        }

        int participant1 = ambulance1.get(index1);
        int participant2 = ambulance2.get(index2);

        ambulance1.set(index1, participant2);
        ambulance2.set(index2, participant1);

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
        pectoSolution.updateRouteCost(route2, delta2);
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
        PECTOModel2 pectoModel = new PECTOModel2(3, 5, 2);
        SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> problem =
                new SimpleOptimizationProblem<>(pectoModel, new PECTOMinimumTimeOF());

        List<PectoSolution> solutions = new ArrayList<>();
        PECTORandomISG isg = new PECTORandomISG(pectoModel);
        PectoSolution pectoSolution2 = (PectoSolution)isg.generate(problem);
        for (int i = 0; i < 2; i++) {
            solutions.add((PectoSolution) isg.generate(problem));
        }

        PECTOMutationSwap p1 = new PECTOMutationSwap();
        PECTOMutationMove p2 = new PECTOMutationMove(false);
        PECTOCrossOverOX pOX = new PECTOCrossOverOX();

        for (int i = 0; i < 1 ; i++) {

            for (Solution s : solutions) {
                System.out.println(s);
            }

            List<Solution> off = pOX.apply(problem, solutions.get(i), solutions.get((i + 1) % solutions.size()));
            for (Solution o : off) {
                solutions.removeFirst();
                solutions.add((PectoSolution) o);
                System.out.println(o);
            }

            for (int j = 0; j < 1; j++) {
                System.out.println("BEF-SWAP-S" + j + ": " + solutions.get(j));
                p1.apply(problem, solutions.get(j));
                System.out.println("BEF-MOVE-S" + j + ": " + solutions.get(j));
                p2.apply(problem, solutions.get(j));
            }
        }
    }
}
