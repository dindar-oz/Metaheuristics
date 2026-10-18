package problems.pecto.aco;

import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Route;

import java.util.Arrays;

/**
 * The pheromone of a run: tau(prev, q) for the candidate patients of every node (directed: which patient follows
 * which) and tau(q, h) for every patient and hospital, kept in [tau_min, tau_max] (MAX-MIN Ant System). The ants read
 * only the weights tau^alpha, computed by {@link #weigh} before they build; nothing changes while they build.
 */
final class Pheromone {

    // the lambda-branching factor counts the candidates whose pheromone is at least tau_min + LAMBDA (tau_max - tau_min)
    private static final double LAMBDA = 0.05;

    private final PECTOProblem problem;
    private final int[][] candidates;
    private final double alpha, rho, pBest, tauMinRatio;
    private final int patientChoices, hospitalChoices;

    private final double[][] patient;           // tau(prev, q), aligned with the candidate lists
    private final double[][] hospital;          // tau(q, h) by patient and hospital index
    final double[][] patientWeight;             // tau^alpha of the iteration, aligned with patient
    final double[][] hospitalWeight;            // tau^alpha of the iteration, aligned with hospital
    private double tauMax, tauMinPatient, tauMinHospital;
    private double minPatientWeight;            // tau_min^alpha of the iteration

    Pheromone(PECTOACO aco, PECTOProblem problem, CandidateLists lists) {
        this.problem = problem;
        this.candidates = lists.patients;
        this.alpha = aco.alpha;
        this.rho = aco.rho;
        this.pBest = aco.pBest;
        this.tauMinRatio = aco.tauMinRatio;
        this.patientChoices = aco.candidates;
        this.hospitalChoices = Math.min(aco.hospitalCandidates, problem.hospitalCount());
        patient = new double[candidates.length][];
        patientWeight = new double[candidates.length][];
        for (int n = 0; n < candidates.length; n++) {
            patient[n] = new double[candidates[n].length];
            patientWeight[n] = new double[candidates[n].length];
        }
        hospital = new double[problem.patientCount()][problem.hospitalCount()];
        hospitalWeight = new double[problem.patientCount()][problem.hospitalCount()];
    }

    // the bounds for the best cost so far: tau_max = 1 / (rho * best cost), tau_min from pBest
    void bound(double bestCost) {
        tauMax = 1 / (rho * Math.max(bestCost, PECTOACO.MIN_COST));
        tauMinPatient = tauMin(patientChoices);
        tauMinHospital = tauMin(hospitalChoices);
    }

    // Stützle & Hoos: the best solution is built with probability pBest once the pheromone has converged; with many
    // patients this is a tiny fraction of tau_max, so tauMinRatio can set a floor
    private double tauMin(int choices) {
        double root = Math.pow(pBest, 1.0 / problem.patientCount());
        double average = Math.max(2, choices / 2.0);
        double tauMin = Math.max(tauMax * (1 - root) / ((average - 1) * root), tauMinRatio * tauMax);
        return Math.min(tauMax, tauMin);
    }

    // every pheromone value at tau_max
    void fill() {
        for (double[] row : patient)
            Arrays.fill(row, tauMax);
        for (double[] row : hospital)
            Arrays.fill(row, tauMax);
    }

    // tau^alpha for the ants of this iteration
    void weigh() {
        for (int n = 0; n < patient.length; n++)
            for (int j = 0; j < patient[n].length; j++)
                patientWeight[n][j] = Math.pow(patient[n][j], alpha);
        for (int k = 0; k < hospital.length; k++)
            for (int h = 0; h < hospital[k].length; h++)
                hospitalWeight[k][h] = Math.pow(hospital[k][h], alpha);
        minPatientWeight = Math.pow(tauMinPatient, alpha);
    }

    // tau_min^alpha: the weight of a patient that is no candidate
    double minPatientWeight() {
        return minPatientWeight;
    }

    // ---- update: evaporate, then deposit, then clamp

    void evaporate() {
        for (double[] row : patient)
            for (int j = 0; j < row.length; j++)
                row[j] *= 1 - rho;
        for (double[] row : hospital)
            for (int h = 0; h < row.length; h++)
                row[h] *= 1 - rho;
    }

    // adds the amount on the choices that build the solution: each patient after its predecessor (if it is one of the
    // predecessor's candidates) and each patient's hospital
    void deposit(PECTOSolution solution, double amount) {
        for (Route route : solution.routes()) {
            int prev = route.ambulance();
            for (int t = 0; t < route.tripCount(); t++) {
                int q = route.patient(t);
                int j = indexOf(candidates[prev], q);
                if (j >= 0)
                    patient[prev][j] += amount;
                hospital[problem.index(q)][problem.index(route.hospital(t))] += amount;
                prev = q;
            }
        }
    }

    // back into [tau_min, tau_max]
    void clamp() {
        for (double[] row : patient)
            for (int j = 0; j < row.length; j++)
                row[j] = Math.min(tauMax, Math.max(tauMinPatient, row[j]));
        for (double[] row : hospital)
            for (int h = 0; h < row.length; h++)
                row[h] = Math.min(tauMax, Math.max(tauMinHospital, row[h]));
    }

    // the lambda-branching factor of the patient pheromone (Gambardella & Dorigo): the mean number of candidates per
    // node above the threshold; about `candidates` at the start, 1 when the colony has converged to one successor
    double branchingFactor() {
        double threshold = tauMinPatient + LAMBDA * (tauMax - tauMinPatient);
        int nodes = 0, branches = 0;
        for (double[] row : patient) {
            if (row.length == 0)
                continue;
            nodes++;
            for (double tau : row)
                if (tau >= threshold)
                    branches++;
        }
        return nodes == 0 ? 0 : (double) branches / nodes;
    }

    private static int indexOf(int[] values, int value) {
        for (int j = 0; j < values.length; j++)
            if (values[j] == value)
                return j;
        return -1;
    }
}
