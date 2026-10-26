package problem.pecto;

import alg.InitialSolutionGenerator;
import alg.greedy.pecto.PECTOGreedyHeuristic;
import alg.greedy.pecto.PECTONearestParticipantEvaluator;
import problem.OptimizationProblem;
import problem.SimpleOptimizationProblem;
import representation.IntegerPermutation;
import representation.PectoSolution;
import representation.Solution;

import java.util.*;

import static utils.RandUtils.generateRandomInt;
import static utils.RandUtils.prob;

public class PECTOHybridISG implements InitialSolutionGenerator<PECTOModel2, IntegerPermutation> {
    PECTOModel2 pectoModel2;
    private final List<Integer> ambulanceList;
    private final List<Integer> patientList;
    private final List<Integer> hospitalList;
    int[] hospitalOccupancy;

    public PECTOHybridISG(PECTOModel2 pectoModel2) {
        this.pectoModel2 = pectoModel2;
        ambulanceList = pectoModel2.getAmbulanceList();
        patientList = pectoModel2.getPatientList();
        hospitalList = pectoModel2.getHospitalList();
        hospitalOccupancy = new int[pectoModel2.getNumHospitals()];
    }

    private boolean isHospitalAvailable(int hospitalIndex) {
        int index = hospitalIndex - pectoModel2.getNumPatients() - pectoModel2.getNumAmbulances();
        return hospitalOccupancy[index] < pectoModel2.getHospitalCapacity() || prob() < 0.05;
    }

    @Override
    public Solution generate(OptimizationProblem<PECTOModel2, IntegerPermutation> problem) {
        PECTOGreedyHeuristic pectoGreedyHeuristic = new PECTOGreedyHeuristic(new PECTONearestParticipantEvaluator(), null);
        PectoSolution greedySolution = (PectoSolution) pectoGreedyHeuristic.solve(pectoModel2,
                (SimpleOptimizationProblem<PECTOModel2, IntegerPermutation>) problem);
        List<Integer> greedyRoute = greedySolution.getRoutes().get(generateRandomInt(pectoModel2.getNumAmbulances()));
        System.out.println("GREEDY:" + greedySolution);
        System.out.println("GREEDY ROUTE:" + greedyRoute.getFirst());

        Arrays.fill(hospitalOccupancy, 0);

        Collections.shuffle(ambulanceList);
        Collections.shuffle(patientList);

        List<List<Integer>> routes = new ArrayList<>();

        for (int ambId : ambulanceList) {
            if (ambId == greedyRoute.getFirst()) {
                routes.add(greedyRoute);
            } else {
                List<Integer> route = new ArrayList<>();
                route.add(ambId);
                routes.add(route);
            }
        }

        boolean[] patientsUsed = new boolean[pectoModel2.getNumPatients()];

        for (int i : greedyRoute) {
            if (pectoModel2.getParticipantType(i).equals(PECTOModel2.ParticipantType.PATIENT)) {
                patientsUsed[i - pectoModel2.getNumAmbulances()] = true;
            } else if (pectoModel2.getParticipantType(i).equals(PECTOModel2.ParticipantType.HOSPITAL)) {
                int hIdx = i - pectoModel2.getNumAmbulances() - pectoModel2.getNumPatients();
                hospitalOccupancy[hIdx]++;
            }
        }

        int index = 0;

        while (index < pectoModel2.getNumPatients()) {
            for (int i = 0; i < ambulanceList.size(); i++) {
                if (index >= pectoModel2.getNumPatients()) break;

                int patientIndex = patientList.get(index) - pectoModel2.getNumAmbulances();

                if (!patientsUsed[patientIndex]) {
                    routes.get(i).add(patientList.get(index));
                    Collections.shuffle(hospitalList);

                    for (int hospitalId : hospitalList) {
                        if (isHospitalAvailable(hospitalId)) {
                            routes.get(i).add(hospitalId);
                            int hIdx = hospitalId - pectoModel2.getNumAmbulances() - pectoModel2.getNumPatients();
                            hospitalOccupancy[hIdx]++;
                            break;
                        }
                    }
                }
                index++;
            }
        }

        return new PectoSolution(routes, hospitalOccupancy, (SimpleOptimizationProblem<PECTOModel2, IntegerPermutation>) problem);
    }

    @Override
    public String toString() {
        return "PECTO-Hybrid-ISG";
    }

    public static void main(String[] args) {
        PECTOModel2 pectoModel2 = new PECTOModel2(200,7290,64);
        SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> problem = new SimpleOptimizationProblem<>(pectoModel2, new PECTOMinimumTimeOF());
        PECTOHybridISG pectoHybridISG = new PECTOHybridISG(pectoModel2);
        PectoSolution pectoSolution2 = (PectoSolution) pectoHybridISG.generate(problem);
        System.out.println(pectoSolution2.value());
    }
}
