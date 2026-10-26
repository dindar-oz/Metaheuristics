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

import static utils.RandUtils.*;

public class PECTOHybridISG2 implements InitialSolutionGenerator<PECTOModel2, IntegerPermutation> {
    PECTOModel2 pectoModel2;
    private final List<Integer> ambulanceList;
    private final List<Integer> patientList;
    private final List<Integer> hospitalList;
    int[] hospitalOccupancy;

    public PECTOHybridISG2(PECTOModel2 pectoModel2) {
        this.pectoModel2 = pectoModel2;
        ambulanceList = pectoModel2.getAmbulanceList();
        patientList = pectoModel2.getPatientList();
        hospitalList = pectoModel2.getHospitalList();
        hospitalOccupancy = new int[pectoModel2.getNumHospitals()];
    }

    private boolean isHospitalAvailable(int hospitalIndex) {
        int index = hospitalIndex - pectoModel2.getNumPatients() - pectoModel2.getNumAmbulances();
        return hospitalOccupancy[index] < pectoModel2.getHospitalCapacity();
    }

    @Override
    public Solution generate(OptimizationProblem<PECTOModel2, IntegerPermutation> problem) {
        PECTOGreedyHeuristic pectoGreedyHeuristic = new PECTOGreedyHeuristic(new PECTONearestParticipantEvaluator(), null);
        PectoSolution greedySolution = (PectoSolution) pectoGreedyHeuristic.solve(pectoModel2,
                (SimpleOptimizationProblem<PECTOModel2, IntegerPermutation>) problem);
        boolean[] usedRoutes = new boolean[pectoModel2.getNumAmbulances()];

        Arrays.fill(hospitalOccupancy, 0);

        Collections.shuffle(ambulanceList);
        Collections.shuffle(patientList);

        List<List<Integer>> routes = new ArrayList<>();

        for (int ambId : ambulanceList) {
            if (rollDice(0.9)) {
                routes.add(greedySolution.getRoutes().get(ambId));
                usedRoutes[ambId] = true;
            } else {
                List<Integer> route = new ArrayList<>();
                route.add(ambId);
                routes.add(route);
            }
        }

        routes.sort(Comparator.comparingInt(List::getFirst));
        boolean[] patientsUsed = new boolean[pectoModel2.getNumPatients()];
        int patientCount = 0;

        for (int i = 0; i < usedRoutes.length; i++) {
            if (usedRoutes[i]) {
                for (int j : greedySolution.getRoutes().get(i)) {
                    if (pectoModel2.getParticipantType(j).equals(PECTOModel2.ParticipantType.PATIENT)) {
                        patientsUsed[j - pectoModel2.getNumAmbulances()] = true;
                        patientCount++;
                    } else if (pectoModel2.getParticipantType(j).equals(PECTOModel2.ParticipantType.HOSPITAL)) {
                        int hIdx = j - pectoModel2.getNumAmbulances() - pectoModel2.getNumPatients();
                        hospitalOccupancy[hIdx]++;
                    }
                }
            }
        }

        int index = 0;

        while (patientCount < pectoModel2.getNumPatients()) {
            for (int i = 0; i < ambulanceList.size(); i++) {
                if (index >= pectoModel2.getNumPatients()) break;

                int patientIndex = patientList.get(index) - pectoModel2.getNumAmbulances();

                if (!usedRoutes[i]) {
                    while (patientsUsed[patientIndex]) {
                        patientIndex = (patientIndex + 1) % pectoModel2.getNumPatients();
                    }


                    routes.get(i).add(patientIndex + pectoModel2.getNumAmbulances());
                    patientsUsed[patientIndex] = true;
                    patientCount++;
                    Collections.shuffle(hospitalList);

                    for (int hospitalId : hospitalList) {
                        if (isHospitalAvailable(hospitalId)) {
                            routes.get(i).add(hospitalId);
                            int hIdx = hospitalId - pectoModel2.getNumAmbulances() - pectoModel2.getNumPatients();
                            hospitalOccupancy[hIdx]++;
                            break;
                        }
                    }

                    if (patientCount == pectoModel2.getNumPatients()) break;
                    index = (index + 1) % pectoModel2.getNumPatients();
                }
            }
        }

        return new PectoSolution(routes, hospitalOccupancy, (SimpleOptimizationProblem<PECTOModel2, IntegerPermutation>) problem);
    }

    @Override
    public String toString() {
        return "PECTO-Hybrid-ISG";
    }

    public static void main(String[] args) {
        PECTOModel2 pectoModel2 = new PECTOModel2(200,5163,64);
        SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> problem = new SimpleOptimizationProblem<>(pectoModel2, new PECTOMinimumTimeOF());
        PECTOHybridISG2 pectoHybridISG = new PECTOHybridISG2(pectoModel2);
        PectoSolution pectoSolution2 = (PectoSolution) pectoHybridISG.generate(problem);
        System.out.println(pectoSolution2.value());
    }
}
