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
import java.util.Arrays;
import java.util.List;

import static utils.RandUtils.rollDice;

/**
 * Alternating Route Crossover (ARX)
 * Highly optimized route-exchange crossover that preserves Ambulance->Patient->Hospital pairings.
 * Uses O(1) Copy & Prune to handle duplicates/overflows and an O(M x H) greedy repair for missing patients.
 */
public class PECTOCrossOverARX implements CrossOverOperator {

    @Override
    public <R extends Representation, PM extends ProblemModel<R>> List<Solution> apply(OptimizationProblem<PM, R> problem, Solution parent1, Solution parent2) {
        PECTOModel2 pectoModel = (PECTOModel2) problem.model();
        SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> pectoProblem = (SimpleOptimizationProblem<PECTOModel2, IntegerPermutation>) problem;

        PectoSolution ps1 = (PectoSolution) parent1;
        PectoSolution ps2 = (PectoSolution) parent2;

        int numAmbs = pectoModel.getNumAmbulances();

        // Epsilon-Greedy Min-Cost mask: 80% chance to pick cheaper route, 20% chance to pick worse
        boolean[] takeFromP1 = new boolean[numAmbs];
        double[] p1Costs = ps1.getRouteCosts();
        double[] p2Costs = ps2.getRouteCosts();

        for (int i = 0; i < numAmbs; i++) {
            boolean p1IsCheaper = p1Costs[i] <= p2Costs[i];
            if (rollDice(0.75)) {
                takeFromP1[i] = p1IsCheaper;
            } else {
                takeFromP1[i] = !p1IsCheaper;
            }
        }

        PectoSolution child = buildChild(ps1, ps2, takeFromP1, pectoModel, pectoProblem);

        return Arrays.asList(child);
    }

    private PectoSolution buildChild(PectoSolution p1, PectoSolution p2, boolean[] takeFromP1,
                                     PECTOModel2 model, SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> problem) {

        int numAmbs = model.getNumAmbulances();
        int numPatients = model.getNumPatients();
        int numHospitals = model.getNumHospitals();
        int hospitalCapacity = model.getHospitalCapacity();

        int patientOffset = numAmbs;
        int hospitalOffset = numAmbs + numPatients;

        boolean[] usedPatients = new boolean[numPatients];
        int[] hospOccupancy = new int[numHospitals];

        List<List<Integer>> childRoutes = new ArrayList<>(numAmbs);

        // --- 1. COPY & PRUNE PHASE ---
        for (int i = 0; i < numAmbs; i++) {
            List<Integer> srcRoute = takeFromP1[i] ? p1.getRoutes().get(i) : p2.getRoutes().get(i);
            List<Integer> destRoute = new ArrayList<>(srcRoute.size());
            destRoute.add(i); // Add ambulance ID

            for (int j = 1; j < srcRoute.size(); j += 2) {
                int p = srcRoute.get(j);
                int h = srcRoute.get(j + 1);

                int pIdx = p - patientOffset;
                int hIdx = h - hospitalOffset;

                // Drop duplicate patients and hospital overflows, freeing up capacity
                if (!usedPatients[pIdx] && hospOccupancy[hIdx] < hospitalCapacity) {
                    destRoute.add(p);
                    destRoute.add(h);
                    usedPatients[pIdx] = true;
                    hospOccupancy[hIdx]++;
                }
            }
            childRoutes.add(destRoute);
        }

        // --- 2. REPAIR PHASE (Best Insertion for Missing Patients) ---
        double[][] distMatrix = model.getDistanceMatrix();

        for (int pIdx = 0; pIdx < numPatients; pIdx++) {
            if (!usedPatients[pIdx]) {
                int p = pIdx + patientOffset;

                // Find nearest available hospital
                int bestH = -1;
                double minHDist = Double.MAX_VALUE;
                for (int hIdx = 0; hIdx < numHospitals; hIdx++) {
                    if (hospOccupancy[hIdx] < hospitalCapacity) {
                        double d = distMatrix[p][hIdx + hospitalOffset];
                        if (d < minHDist) {
                            minHDist = d;
                            bestH = hIdx + hospitalOffset;
                        }
                    }
                }

                // 1. Find the nearest ambulance to the patient
                int targetR = -1;
                double minDistToAmb = Double.MAX_VALUE;
                for (int r = 0; r < numAmbs; r++) {
                    List<Integer> route = childRoutes.get(r);
                    int depot = route.get(0);
                    int lastNode = route.get(route.size() - 1);

                    double d1 = distMatrix[depot][p];
                    double d2 = distMatrix[lastNode][p];
                    double d = Math.min(d1, d2);

                    if (d < minDistToAmb) {
                        minDistToAmb = d;
                        targetR = r;
                    }
                }

                // 2. Perform Best Insertion ONLY on the targeted route
                int bestInsertIdx = -1;
                double minPenalty = Double.MAX_VALUE;

                if (targetR != -1) {
                    List<Integer> route = childRoutes.get(targetR);
                    for (int idx = 1; idx <= route.size(); idx += 2) {
                        int prevNode = route.get(idx - 1);
                        int nextNode = (idx < route.size()) ? route.get(idx) : -1;

                        double currentDist = 0;
                        if (nextNode != -1) {
                            currentDist = distMatrix[prevNode][nextNode];
                        }

                        double newDist = distMatrix[prevNode][p];
                        if (bestH != -1) {
                            newDist += distMatrix[p][bestH];
                            if (nextNode != -1) {
                                newDist += distMatrix[bestH][nextNode];
                            }
                        }

                        double penalty = newDist - currentDist;

                        if (penalty < minPenalty) {
                            minPenalty = penalty;
                            bestInsertIdx = idx;
                        }
                    }
                }

                // 3. Insert missing patient
                if (bestH != -1 && targetR != -1) {
                    childRoutes.get(targetR).add(bestInsertIdx, bestH);
                    childRoutes.get(targetR).add(bestInsertIdx, p);
                    hospOccupancy[bestH - hospitalOffset]++;
                }
            }
        }

        return new PectoSolution(childRoutes, hospOccupancy, problem);
    }

    @Override
    public String toString() {
        return "ARX";
    }

    public static void main(String[] args) {
        PECTOModel2 pectoModel = new PECTOModel2(3, 5, 2);
        SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> problem =
                new SimpleOptimizationProblem<>(pectoModel, new PECTOMinimumTimeOF());

        PECTOGreedyHeuristic pectoGreedyHeuristic = new PECTOGreedyHeuristic(new PECTONearestParticipantEvaluator(), null);
        
        System.out.println("Generating Parents...");
        PectoSolution p1 = (PectoSolution) pectoGreedyHeuristic.solve(problem.model(), problem);
        PectoSolution p2 = (PectoSolution) pectoGreedyHeuristic.solve(problem.model(), problem);

        PECTOCrossOverARX arx = new PECTOCrossOverARX();
        System.out.println("Parent 1 Cost: " + p1);
        System.out.println("Parent 2 Cost: " + p2);

        System.out.println("Applying ARX Crossover...");
        long start = System.currentTimeMillis();
        List<Solution> offsprings = arx.apply(problem, p1, p2);
        long end = System.currentTimeMillis();

        System.out.println("Processing took: " + (end - start) + "ms");
        System.out.println("Offspring Cost: " + offsprings.get(0));
    }
}
