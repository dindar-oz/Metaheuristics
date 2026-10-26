package problem.pecto;

import alg.InitialSolutionGenerator;
import problem.OptimizationProblem;
import problem.SimpleOptimizationProblem;
import representation.IntegerPermutation;
import representation.PectoSolution;
import representation.Solution;

import java.util.*;

public class PECTORandomISG implements InitialSolutionGenerator<PECTOModel2, IntegerPermutation> {
    PECTOModel2 pectoModel2;
    private final List<Integer> ambulanceList;
    private final List<Integer> patientList;
    private final List<Integer> hospitalList;
    int[] hospitalOccupancy;

    public PECTORandomISG(PECTOModel2 pectoModel2) {
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
        List<List<Integer>> routes = new ArrayList<>();
        Arrays.fill(hospitalOccupancy, 0);

        Collections.shuffle(ambulanceList);
        Collections.shuffle(patientList);

        for (int ambId : ambulanceList) {
            List<Integer> route = new ArrayList<>();
            route.add(ambId);
            routes.add(route);
        }

        int patientIndex = 0;

        while (patientIndex < pectoModel2.getNumPatients()) {
            for (int i = 0; i < ambulanceList.size(); i++) {
                if (patientIndex >= pectoModel2.getNumPatients()) break;

                routes.get(i).add(patientList.get(patientIndex));
                patientIndex++;
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
        }

        return new PectoSolution(routes, hospitalOccupancy, (SimpleOptimizationProblem<PECTOModel2, IntegerPermutation>) problem);
    }

    @Override
    public String toString() {
        return "PECTO-Random-ISG";
    }

    public static void main(String[] args) {
        PECTOModel2 pectoModel2 = new PECTOModel2(200,7290,64);
        SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> problem = new SimpleOptimizationProblem<>(pectoModel2, new PECTOMinimumTimeOF());
        PECTORandomISG pectoRandomISG2 = new PECTORandomISG(pectoModel2);
        PectoSolution pectoSolution2 = (PectoSolution) pectoRandomISG2.generate(problem);
        System.out.println(pectoSolution2.value());
    }
}
