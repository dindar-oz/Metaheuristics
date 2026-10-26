package problems.pecto.aco.gui;

import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Route;

import java.util.Arrays;

/**
 * The diagnosis' copy of problems.pecto.aco.Pheromone, plus snapshots: tau(prev, q) for the candidate patients of
 * every node and tau(q, h) for every patient and hospital, kept in [tau_min, tau_max]. Keep the update identical to
 * the original (DiagnosticColonyTest checks the runs).
 */
final class PheromoneTrails {

    static final double LAMBDA = 0.05;

    private final PECTOProblem problem;
    private final int[][] candidates;
    private final double alpha, rho, pBest, tauMinRatio;
    private final int patientChoices, hospitalChoices;

    private final double[][] patient;           // tau(prev, q), aligned with the candidate lists
    private final double[][] hospital;          // tau(q, h) by patient and hospital index
    final double[][] patientWeight;             // tau^alpha of the iteration
    final double[][] hospitalWeight;
    private double tauMax, tauMinPatient, tauMinHospital;
    private double minPatientWeight;

    PheromoneTrails(ColonySettings s, PECTOProblem problem, Candidates lists) {
        this.problem = problem;
        this.candidates = lists.patients;
        this.alpha = s.alpha();
        this.rho = s.rho();
        this.pBest = s.pBest();
        this.tauMinRatio = s.tauMinRatio();
        this.patientChoices = s.candidates();
        this.hospitalChoices = Math.min(s.hospitalCandidates(), problem.hospitalCount());
        patient = new double[candidates.length][];
        patientWeight = new double[candidates.length][];
        for (int n = 0; n < candidates.length; n++) {
            patient[n] = new double[candidates[n].length];
            patientWeight[n] = new double[candidates[n].length];
        }
        hospital = new double[problem.patientCount()][problem.hospitalCount()];
        hospitalWeight = new double[problem.patientCount()][problem.hospitalCount()];
    }

    void bound(double bestCost) {
        tauMax = 1 / (rho * Math.max(bestCost, ColonyRun.MIN_COST));
        tauMinPatient = tauMin(patientChoices);
        tauMinHospital = tauMin(hospitalChoices);
    }

    private double tauMin(int choices) {
        double root = Math.pow(pBest, 1.0 / problem.patientCount());
        double average = Math.max(2, choices / 2.0);
        double tauMin = Math.max(tauMax * (1 - root) / ((average - 1) * root), tauMinRatio * tauMax);
        return Math.min(tauMax, tauMin);
    }

    void fill() {
        for (double[] row : patient)
            Arrays.fill(row, tauMax);
        for (double[] row : hospital)
            Arrays.fill(row, tauMax);
    }

    void weigh() {
        for (int n = 0; n < patient.length; n++)
            for (int j = 0; j < patient[n].length; j++)
                patientWeight[n][j] = Math.pow(patient[n][j], alpha);
        for (int k = 0; k < hospital.length; k++)
            for (int h = 0; h < hospital[k].length; h++)
                hospitalWeight[k][h] = Math.pow(hospital[k][h], alpha);
        minPatientWeight = Math.pow(tauMinPatient, alpha);
    }

    double minPatientWeight() {
        return minPatientWeight;
    }

    void evaporate() {
        for (double[] row : patient)
            for (int j = 0; j < row.length; j++)
                row[j] *= 1 - rho;
        for (double[] row : hospital)
            for (int h = 0; h < row.length; h++)
                row[h] *= 1 - rho;
    }

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

    void clamp() {
        for (double[] row : patient)
            for (int j = 0; j < row.length; j++)
                row[j] = Math.min(tauMax, Math.max(tauMinPatient, row[j]));
        for (double[] row : hospital)
            for (int h = 0; h < row.length; h++)
                row[h] = Math.min(tauMax, Math.max(tauMinHospital, row[h]));
    }

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

    // ---- diagnosis: snapshots and reading

    /** A copy of the pheromone values and bounds. Immutable by convention: nothing writes its arrays. */
    record Snapshot(double[][] patient, double[][] hospital, double tauMax, double tauMinPatient, double tauMinHospital) {

        long bytes() {
            long values = 0;
            for (double[] row : patient)
                values += row.length;
            for (double[] row : hospital)
                values += row.length;
            return 8 * values + 16L * (patient.length + hospital.length);
        }
    }

    Snapshot snapshot() {
        return new Snapshot(deepCopy(patient), deepCopy(hospital), tauMax, tauMinPatient, tauMinHospital);
    }

    // the values and bounds of the snapshot; the weights are computed by the next weigh()
    void restore(Snapshot s) {
        for (int n = 0; n < patient.length; n++)
            System.arraycopy(s.patient()[n], 0, patient[n], 0, patient[n].length);
        for (int k = 0; k < hospital.length; k++)
            System.arraycopy(s.hospital()[k], 0, hospital[k], 0, hospital[k].length);
        tauMax = s.tauMax();
        tauMinPatient = s.tauMinPatient();
        tauMinHospital = s.tauMinHospital();
    }

    double tauMax() {
        return tauMax;
    }

    double tauMinPatient() {
        return tauMinPatient;
    }

    double tauMinHospital() {
        return tauMinHospital;
    }

    private static double[][] deepCopy(double[][] a) {
        double[][] c = new double[a.length][];
        for (int i = 0; i < a.length; i++)
            c[i] = a[i].clone();
        return c;
    }
}
