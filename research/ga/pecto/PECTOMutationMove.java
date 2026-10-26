package alg.ga.pecto;

import alg.ga.MutationOperation;
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

public class PECTOMutationMove implements MutationOperation {
    boolean checkImprovement;
    int route1;
    int route2;
    int index1;
    int index2;
    int solutionIndex1;
    int solutionIndex2;
    List<Integer> ambulance1 = new ArrayList<>();
    List<Integer> ambulance2 = new ArrayList<>();

    public PECTOMutationMove(boolean checkImprovement) {
        this.checkImprovement = checkImprovement;
    }

    @Override
    public <R extends Representation, PM extends ProblemModel<R>> void apply(OptimizationProblem<PM, R> problem, Solution s) {
        PECTOModel2 pectoModel = (PECTOModel2) problem.model();
        int numAmbulances = pectoModel.getNumAmbulances();
        PectoSolution pectoSolution = (PectoSolution) s;

        route1 = findMostExpensiveRoute(pectoSolution);
//        route1 = generateRandomInt(numAmbulances);
//
//        while (!hasPatient(pectoModel, pectoSolution, route1)) {
//            route1 = (route1 + 1) % numAmbulances;
//        }

        ambulance1 = pectoSolution.getRoutes().get(route1);
        index1 = generateRandomInt(ambulance1.size());
        solutionIndex1 = findSolutionIndex(pectoSolution, route1, index1);

        while (pectoModel.getParticipantType(((IntegerPermutation)(pectoSolution.getRepresentation())).get(solutionIndex1))
                != ParticipantType.PATIENT) {
            index1 = (index1 + 1) % ambulance1.size();
            solutionIndex1 = findSolutionIndex(pectoSolution, route1, index1);
        }

        boolean isImproving = false;
        int iteration = numAmbulances - 1;

        do {
            route2 = generateRandomInt(numAmbulances);

            while (route2 == route1) {
                route2 = (route2 + 1) % numAmbulances;
            }

            ambulance2 = pectoSolution.getRoutes().get(route2);
            index2 = generateRandomInt(ambulance2.size());

            double delta1 = 0;
            double delta2 = 0;
            int ambSize2 = ambulance2.size();
            double bestDelta = 0;
            int bestIndex2 = 0;
            double bestDelta1 = 0;
            double bestDelta2 = 0;
            delta1 = findDelta1(problem, pectoSolution, solutionIndex1);


            for (int i = 0; i < ambSize2; i++) {
                solutionIndex2 = findSolutionIndex(pectoSolution, route2, index2);

                if (!pectoModel.getParticipantType(((IntegerPermutation) pectoSolution.getRepresentation()).get(solutionIndex2))
                        .equals(ParticipantType.PATIENT)) {
                    delta2 = findDelta2(problem, pectoSolution, solutionIndex1, solutionIndex2);

                    if (!checkImprovement) {
                        bestIndex2 = index2;
                        bestDelta1 = delta1;
                        bestDelta2 = delta2;
                        break;
                    }

                    if ((pectoSolution.calculateDeltaWeightedSum(route1, route2, delta1, delta2)) < 0) {
                        bestDelta = delta1 + delta2;
                        bestIndex2 = index2;
                        bestDelta1 = delta1;
                        bestDelta2 = delta2;
                        isImproving = true;
                        break;
                    }
                }

                index2 = (index2 + 1) % ambSize2;
            }

            if (isImproving || !checkImprovement) {
                ambulance2.add(Math.min(bestIndex2 + 1, ambulance2.size()), ambulance1.get(index1));
                ambulance2.add(Math.min(bestIndex2 + 2, ambulance2.size()), ambulance1.get(index1 + 1));
                ambulance1.remove(index1);
                ambulance1.remove(index1);

                pectoSolution.updateRouteCost(route1, bestDelta1);
                pectoSolution.updateRouteCost(route2, bestDelta2);
                pectoSolution.syncFromRoute(Math.min(route1, route2));
            }

            iteration--;

            if (iteration == 0) {
                break;
            }
        } while (checkImprovement && !isImproving);
    }

    private int findMostExpensiveRoute(PectoSolution s) {
        int maxRoute = 0;
        for (int i = 1; i < s.values().length; i++) {
            if (s.value(i) > s.value(maxRoute)) maxRoute = i;
        }
        return maxRoute;
    }

    private boolean hasPatient(PECTOModel2 model, PectoSolution s, int route) {
        List<Integer> ambulanceList = s.getRoutes().get(route);
        for (int i = 0; i < ambulanceList.size(); i++) {
            int solutionIndex = findSolutionIndex(s, route, i);
            if (model.getParticipantType(
                    ((IntegerPermutation) s.getRepresentation()).get(solutionIndex)
            ).equals(ParticipantType.PATIENT)) {
                return true;
            }
        }
        return false;
    }

    private double findDelta1(OptimizationProblem problem, PectoSolution solution, int solutionIndex1) {
        List<Integer> initialList = extractSegment1(solution, solutionIndex1, solutionIndex1);
        List<Integer> finalList = new ArrayList<>();
        double finalValue = 0;

        if ((solutionIndex1 + 2) < solution.size()) {
            int solution1 = ((IntegerPermutation)(solution.getRepresentation())).get(solutionIndex1 - 1);
            int solution2 = ((IntegerPermutation)(solution.getRepresentation())).get(solutionIndex1 + 2);
            finalList.add(solution1);
            finalList.add(solution2);
        }

        double initialValue = problem.getObjective().value(problem.model(), new IntegerPermutation(initialList));

        if (!finalList.isEmpty()) {
            finalValue = problem.getObjective().value(problem.model(), new IntegerPermutation(finalList));
        }

        return finalValue - initialValue;
    }

    private double findDelta2(OptimizationProblem problem, PectoSolution solution, int solutionIndex1, int solutionIndex2) {
        List<Integer> initialList = new ArrayList<>();
        List<Integer> finalList = extractSegment2(solution, solutionIndex2, solutionIndex1);
        double initialValue = 0.0;

        if ((solutionIndex2 + 1) < solution.size()) {
            int solution1 = ((IntegerPermutation)(solution.getRepresentation())).get(solutionIndex2);
            int solution2 = ((IntegerPermutation)(solution.getRepresentation())).get(solutionIndex2 + 1);
            initialList.add(solution1);
            initialList.add(solution2);
        }

        double finalValue = problem.getObjective().value(problem.model(), new IntegerPermutation(finalList));

        if (!initialList.isEmpty()) {
            initialValue = problem.getObjective().value(problem.model(), new IntegerPermutation(initialList));
        }

        return (finalValue - initialValue);
    }

    private List<Integer> extractSegment1(PectoSolution s, int index1, int index2) {
        List<Integer> list = new ArrayList<>();

        if ((index1 - 1) >= 0) {
            list.add(((IntegerPermutation)(s.getRepresentation())).get(index1 - 1));
        }

        list.add(((IntegerPermutation)(s.getRepresentation())).get(index2));

        if ((index1 + 1) <= s.size() - 1) {
            list.add(((IntegerPermutation)(s.getRepresentation())).get(index1 + 1));
        }

        if ((index1 + 2) <= s.size() - 1) {
            list.add(((IntegerPermutation)(s.getRepresentation())).get(index1 + 2));
        }

        return list;
    }

    private List<Integer> extractSegment2(PectoSolution s, int index1, int index2) {
        List<Integer> list = new ArrayList<>();

        if ((index1) >= 0) {
            list.add(((IntegerPermutation)(s.getRepresentation())).get(index1));
        }

        list.add(((IntegerPermutation)(s.getRepresentation())).get(index2));

        if ((index2 + 1) <= s.size() - 1) {
            list.add(((IntegerPermutation)(s.getRepresentation())).get(index2 + 1));
        }

        if ((index1 + 1) <= s.size() - 1) {
            list.add(((IntegerPermutation)(s.getRepresentation())).get(index1 + 1));
        }

        return list;
    }

    private int findSolutionIndex(PectoSolution solution, int route, int index) {
        int solutionIndex = index;

        for (int i = 0; i < route; i++) {
            solutionIndex += solution.getRoutes().get(i).size();
        }

        return solutionIndex;
    }

    @Override
    public String toString() {
        return checkImprovement ? "SMART" : "RANDOM";
    }

    public static void main(String[] args) {
        PECTOModel2 pectoModel = new PECTOModel2(200, 2000, 64);
        SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> problem =
                new SimpleOptimizationProblem<>(pectoModel, new PECTOMinimumTimeOF());
        PECTORandomISG pectoRandomISG = new PECTORandomISG(pectoModel);

        PECTOGreedyHeuristic pectoGreedyHeuristic = new PECTOGreedyHeuristic(new PECTONearestParticipantEvaluator(),null);
        PectoSolution solution = (PectoSolution) pectoGreedyHeuristic.solve(pectoModel, problem);


        PECTOMutationMove mutation = new PECTOMutationMove(true);
        PECTOMutationSwap2 mutationSwap3 = new PECTOMutationSwap2();

        System.out.println("======== SOLUTION " + (0 + 1) + " ========");
        System.out.println(solution);
        System.out.print("BEFORE: [");
        for (Double d : solution.getRouteCosts()) {
            System.out.print(d + " ");
        }
        System.out.println("]");

        for (int j = 0; j < 100; j++) {
            mutationSwap3.apply(problem, solution);
        }

        System.out.println(solution);
        System.out.print("AFTER:[");
        for (Double d : solution.getRouteCosts()) {
            System.out.print(d + " ");
        }
        System.out.println("]");
    }
}
