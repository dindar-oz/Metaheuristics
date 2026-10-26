package alg.ga.pecto;

import alg.ga.MutationOperation;
import alg.greedy.pecto.PECTOGreedyHeuristic;
import alg.greedy.pecto.PECTONearestParticipantEvaluator;
import problem.OptimizationProblem;
import problem.ProblemModel;
import problem.SimpleOptimizationProblem;
import problem.pecto.PECTOMinimumTimeOF;
import problem.pecto.PECTOModel2;
import representation.IntegerPermutation;
import representation.PectoSolution;
import representation.Representation;
import representation.Solution;

import java.util.ArrayList;
import java.util.List;

import static problem.pecto.PECTOModel2.ParticipantType;
import static utils.RandUtils.generateRandomInt;

/**
 * Moves a random patient-hospital pair from the most expensive route
 * to a random position in the least expensive route.
 */

public class PECTOStagnationMove implements MutationOperation {
    int route1;
    int route2;
    int index1;
    int index2;
    int solutionIndex1;
    int solutionIndex2;
    List<Integer> ambulance1 = new ArrayList<>();
    List<Integer> ambulance2 = new ArrayList<>();

    @Override
    public <R extends Representation, PM extends ProblemModel<R>> void apply(OptimizationProblem<PM, R> problem, Solution s) {
        PECTOModel2 pectoModel = (PECTOModel2) problem.model();
        int numAmbulances = pectoModel.getNumAmbulances();
        PectoSolution pectoSolution = (PectoSolution) s;

        int[] routes = findMostAndLeastExpensiveRoute(pectoSolution);
        route1 = routes[0];
        route2 = routes[1];
        ambulance1 = pectoSolution.getRoutes().get(route1);

        index1 = generateRandomInt(ambulance1.size());
        solutionIndex1 = findSolutionIndex(pectoSolution, route1, index1);

        while (pectoModel.getParticipantType(((IntegerPermutation)(pectoSolution.getRepresentation())).get(solutionIndex1))
                != ParticipantType.PATIENT) {
            index1 = (index1 + 1) % ambulance1.size();
            solutionIndex1 = findSolutionIndex(pectoSolution, route1, index1);
        }

        double delta1 = findDelta1(problem, pectoSolution, solutionIndex1);

        while ((route2 == route1)) {
            route2 = (route2 + 1) % numAmbulances;
        }

        ambulance2 = pectoSolution.getRoutes().get(route2);
        index2 = generateRandomInt(ambulance2.size());
        solutionIndex2 = findSolutionIndex(pectoSolution, route2, index2);

        while (pectoModel.getParticipantType(((IntegerPermutation) pectoSolution.getRepresentation()).get(solutionIndex2))
                .equals(ParticipantType.PATIENT)) {
            index2 = (index2 + 1) % ambulance2.size();
            solutionIndex2 = findSolutionIndex(pectoSolution, route2, index2);
        }

        double delta2 = findDelta2(problem, pectoSolution, solutionIndex1, solutionIndex2);

        ambulance2.add(Math.min(index2 + 1, ambulance2.size()), ambulance1.get(index1));
        ambulance2.add(Math.min(index2 + 2, ambulance2.size()), ambulance1.get(index1 + 1));

        ambulance1.remove(index1);
        ambulance1.remove(index1);

        pectoSolution.updateRouteCost(route1, delta1);
        pectoSolution.updateRouteCost(route2, delta2);
        pectoSolution.syncFromRoute(Math.min(route1, route2));
    }

    private int[] findMostAndLeastExpensiveRoute(PectoSolution s) {
        int maxRoute = 0;
        int minRoute = 0;
        for (int i = 1; i < s.values().length; i++) {
            if (s.value(i) > s.value(maxRoute)) maxRoute = i;
            if (s.value(i) < s.value(minRoute)) minRoute = i;
        }
        return new int[]{maxRoute, minRoute};
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
        return "STAG";
    }

    public static void main(String[] args) {
        PECTOModel2 pectoModel = new PECTOModel2(15, 51, 2);
        SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> problem =
                new SimpleOptimizationProblem<>(pectoModel, new PECTOMinimumTimeOF());

        PectoSolution[] solutions = new PectoSolution[] {
                (PectoSolution) new PECTOGreedyHeuristic(new PECTONearestParticipantEvaluator(), null).solve(pectoModel, problem)
        };

        PECTOStagnationMove mutation = new PECTOStagnationMove();
        PECTOMutationSwap mutationSwap3 = new PECTOMutationSwap();

        System.out.println("======== SOLUTION " + (0 + 1) + " ========");
        System.out.println("BEFORE:");
        System.out.println(solutions[0]);
        for (int j = 0; j < 10; j++) {
            for (int i = 0; i < solutions.length; i++) {


                mutation.apply(problem, solutions[i]);


            }
        }
        System.out.println("AFTER:");
        System.out.println(solutions[0]);
        System.out.println();
    }
}
