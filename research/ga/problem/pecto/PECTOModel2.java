package problem.pecto;

import problem.ProblemModel;
import representation.IntegerPermutation;

import java.util.*;

import static utils.RandUtils.*;

public class PECTOModel2 implements ProblemModel<IntegerPermutation> {
    private double[][] distanceMatrix;
    private double[][] shortestPathMatrix;
    private final int numAmbulances;
    private final int numPatients;
    private final int numHospitals;
    private final List<Integer> ambulanceList;
    private final List<Integer> patientList;
    private final List<Integer> hospitalList;
    private final Urgency[] patientUrgency;
    private final int hospitalCapacity;
    private final double[][] coordinates;

    public enum ParticipantType {
        AMBULANCE, PATIENT, HOSPITAL
    }

    public enum Urgency {
        STABLE(1.0, 5000),
        LIGHT(0.8, 4000),
        MODERATE(0.6, 3000),
        HEAVY(0.4, 2000),
        CRITICAL(0.2, 1000);

        private final double multiplier;
        private final double transportTime;

        Urgency(double multiplier, double transportTime) {
            this.multiplier = multiplier;
            this.transportTime = transportTime;
        }

        public double getMultiplier() {
            return multiplier;
        }
        public double getTransportTime() {return transportTime;}
    }

    public PECTOModel2(int numAmbulances, int numPatients, int numHospitals) {
        this.numAmbulances = numAmbulances;
        this.numPatients = numPatients;
        this.numHospitals = numHospitals;
        this.hospitalCapacity = (int)((14522 * 0.36) / 64);
        coordinates = generateCoordinates();
        buildDistanceMatrix();
//        buildShortestPathMatrix();
        patientList = new ArrayList<>();
        ambulanceList = new ArrayList<>();
        hospitalList = new ArrayList<>();
        patientUrgency = buildPatientUrgency();
        addPatients();
        addAmbulances();
        addHospitals();
    }

    private void addAmbulances() {
        for (int a = 0; a < numAmbulances; a++) {
            ambulanceList.add(a);
        }
    }

    private void addPatients() {
        for (int p = 0; p < numPatients; p++) {
            patientList.add(p + numAmbulances);
        }
    }

    private void addHospitals() {
        for (int h = 0; h < numHospitals; h++) {
            hospitalList.add(h + numPatients + numAmbulances);
        }
    }

    private Urgency[] buildPatientUrgency() {
        Urgency[] urgency = new Urgency[numPatients];
        Urgency[] values = Urgency.values();

        for (int i = 0; i < numPatients; i++) {
            urgency[i] = values[generateRandomInt(5)];
        }

        return urgency;
    }
    public double getPatientUrgencyMultiplier(int patientID) {
        return patientUrgency[patientID - numAmbulances].getMultiplier();
    }

    public double getPatientUrgencyTransportTime(int patientID) {
        return patientUrgency[patientID - numAmbulances].getTransportTime();
    }

    public List<Integer> getPatientList() {
        return new ArrayList<>(patientList);
    }

    public List<Integer> getHospitalList() {return new ArrayList<>(hospitalList);}

    public List<Integer> getAmbulanceList() {
        return new ArrayList<>(ambulanceList);
    }

    public int getSize() { return numAmbulances + numPatients + numHospitals; }

    public int getHospitalCapacity() {
        return hospitalCapacity;
    }

    private double[][] generateCoordinates() {
        double[][] coordinates = new double[getSize()][2];

        for (int i = 0; i < getSize(); i++) {
            coordinates[i][0] = 1e-8 + generateRandomDouble(350);
            coordinates[i][1] = 1e-8 + generateRandomDouble(350);
        }

        return coordinates;
    }

    private void buildDistanceMatrix() {
        distanceMatrix = new double[getSize()][getSize()];
        double dx;
        double dy;

        for (int i = 0; i < getSize(); i++) {
            for (int j = i + 1; j < getSize(); j++) {
                dx = coordinates[i][0] - coordinates[j][0];
                dy = coordinates[i][1] - coordinates[j][1];

                if (rollDice(1)) {
                    distanceMatrix[i][j] = distanceMatrix[j][i] = Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
                } else {
                    distanceMatrix[i][j] = distanceMatrix[j][i] = 1e5;
                }
            }
        }
    }

    public void buildShortestPathMatrix() {
        shortestPathMatrix = new double[getSize()][getSize()];

        for (int i = 0; i < getSize(); i++) {
            for (int j = 0; j < getSize(); j++) {
                shortestPathMatrix[i][j] = distanceMatrix[i][j];
            }
            shortestPathMatrix[i][i] = 0.0;
        }

        for (int k = 0; k < getSize(); k++) {
            for (int i = 0; i < getSize(); i++) {
                for (int j = 0; j < getSize(); j++) {
                    double path_k = shortestPathMatrix[i][k] + shortestPathMatrix[k][j];
                    if (path_k < shortestPathMatrix[i][j]) {
                        shortestPathMatrix[i][j] = path_k;
                    }
                }
            }
        }
    }

    public double shortestPath(int from, int to) {
        double shortestPath = distanceMatrix[from][to];

        for (int i = 0; i < getSize(); i++) {
            if (from != i && to != i &&
                    distanceMatrix[from][i] + distanceMatrix[i][to] < distanceMatrix[from][to] &&
                    distanceMatrix[from][i] + distanceMatrix[i][to] < shortestPath) {
                shortestPath = distanceMatrix[from][i] + distanceMatrix[i][to];
            }
        }

        return shortestPath;
    }

    public double[][] getDistanceMatrix() {
        return distanceMatrix;
    }

    public double[][] getShortestPathMatrix() {
        return shortestPathMatrix;
    }

    public int getNumAmbulances() {
        return numAmbulances;
    }

    public int getNumPatients() {
        return numPatients;
    }

    public int getNumHospitals() {
        return numHospitals;
    }

    public ParticipantType getParticipantType(int index) {
        if (index < numAmbulances) return ParticipantType.AMBULANCE;
        if (index < numAmbulances + numPatients) return ParticipantType.PATIENT;
        if (index < numAmbulances + numPatients + numHospitals) return ParticipantType.HOSPITAL;
        return null;
    }

    @Override
    public boolean isFeasable(IntegerPermutation integerPermutation) {
        if (integerPermutation == null || integerPermutation.size() == 0) {
            return false;
        }

        int i = 0;
        int totalSize = integerPermutation.size();

        while (i < totalSize) {
            if (i + 2 >= totalSize) {
                return false;
            }

            ParticipantType type1 = getParticipantType(integerPermutation.get(i));
            ParticipantType type2 = getParticipantType(integerPermutation.get(i + 1));
            ParticipantType type3 = getParticipantType(integerPermutation.get(i + 2));

            if (!type1.equals(ParticipantType.AMBULANCE) || !type2.equals(ParticipantType.PATIENT) || !type3.equals(ParticipantType.HOSPITAL)) {
                return false;
            }
            i += 3;

            while (i < totalSize && !getParticipantType(integerPermutation.get(i)).equals(ParticipantType.AMBULANCE)) {
                if (i + 1 >= totalSize) {
                    return false;
                }

                ParticipantType type4 = getParticipantType(integerPermutation.get(i));
                ParticipantType type5 = getParticipantType(integerPermutation.get(i + 1));

                if (!type4.equals(ParticipantType.PATIENT) || !type5.equals(ParticipantType.HOSPITAL)) {
                    return false;
                }
                i += 2;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "PECTO: A" + numAmbulances + " P" + numPatients + " H" + numHospitals;
    }
}
