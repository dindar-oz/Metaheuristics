package problems.pecto.aco;

import problems.pecto.PECTOProblem;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * What the ants of a run know about the instance, computed once and only read afterwards: the candidate patients of
 * every node and the orders by distance. Patients and hospitals are participant IDs, except where an array is indexed
 * by patient index k or hospital index (problem.index).
 */
final class CandidateLists {

    private final PECTOProblem problem;
    private final int ambulances;

    // patients[n]: the nearest patients of node n (an ambulance or a patient ID), by urgency-weighted distance w_q D(n, q)
    final int[][] patients;
    // by patient index: the nodes whose candidates include the patient
    final int[][] candidateOf;
    // byDistance[l]: all patients by urgency-weighted distance from location l, where an ambulance can be: its start
    // (l = its ID) or a hospital (l = A + hospital index); see location
    final int[][] byDistance;
    // by patient index: the hospital indices by distance from the patient
    final int[][] hospitalOrder;

    // `count` candidates per node (fewer if the instance has fewer patients)
    CandidateLists(PECTOProblem problem, int count) {
        this.problem = problem;
        ambulances = problem.ambulanceCount();
        int patientCount = problem.patientCount(), hospitals = problem.hospitalCount();

        int nodes = ambulances + patientCount;
        patients = new int[nodes][];
        for (int n = 0; n < nodes; n++)
            patients[n] = nearestPatients(n, count);

        int[] counts = new int[patientCount];
        for (int[] c : patients)
            for (int q : c)
                counts[problem.index(q)]++;
        candidateOf = new int[patientCount][];
        for (int k = 0; k < patientCount; k++)
            candidateOf[k] = new int[counts[k]];
        Arrays.fill(counts, 0);
        for (int n = 0; n < nodes; n++)
            for (int q : patients[n])
                candidateOf[problem.index(q)][counts[problem.index(q)]++] = n;

        byDistance = new int[ambulances + hospitals][];
        for (int l = 0; l < byDistance.length; l++) {
            int location = l < ambulances ? l : problem.hospitalId(l - ambulances);
            byDistance[l] = IntStream.range(0, patientCount).map(problem::patientId).boxed()
                    .sorted(Comparator.comparingDouble(q -> problem.urgencyWeight(q) * problem.distance(location, q)))
                    .mapToInt(Integer::intValue).toArray();
        }

        hospitalOrder = new int[patientCount][];
        for (int k = 0; k < patientCount; k++) {
            int patient = problem.patientId(k);
            hospitalOrder[k] = IntStream.range(0, hospitals).boxed()
                    .sorted(Comparator.comparingDouble(h -> problem.distance(patient, problem.hospitalId(h))))
                    .mapToInt(Integer::intValue).toArray();
        }
    }

    // the index into byDistance of a place an ambulance can be at: its start (its ID) or a hospital (by ID)
    int location(int at) {
        return problem.isAmbulance(at) ? at : ambulances + problem.index(at);
    }

    // the `count` nearest patients of the node, nearest first (insertion into a sorted array, ties: lower index first)
    private int[] nearestPatients(int node, int count) {
        int patientCount = problem.patientCount();
        int size = Math.min(count, problem.isPatient(node) ? patientCount - 1 : patientCount);
        int[] nearest = new int[size];
        double[] distance = new double[size];
        int filled = 0;
        for (int k = 0; k < patientCount; k++) {
            int q = problem.patientId(k);
            if (q == node)
                continue;
            double d = problem.urgencyWeight(q) * problem.distance(node, q);
            if (filled == size && d >= distance[size - 1])
                continue;
            int i = filled < size ? filled++ : size - 1;
            while (i > 0 && distance[i - 1] > d) {
                nearest[i] = nearest[i - 1];
                distance[i] = distance[i - 1];
                i--;
            }
            nearest[i] = q;
            distance[i] = d;
        }
        return nearest;
    }
}
