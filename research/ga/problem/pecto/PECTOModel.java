package problem.pecto;

import problem.ProblemModel;
import representation.IntegerPermutation;

import java.util.*;

import static utils.RandUtils.*;

public class PECTOModel implements ProblemModel<IntegerPermutation> {
    private int[][] distanceMatrix;
    private final int numAmbulances;
    private final int numPatients;
    private final int numHospitals;
    private final List<Integer> ambulanceList;
    private final List<Integer> patientList;
    private final List<Integer> hospitalList;
    private final Urgency[] patientUrgency;
    private final int hospitalCapacity;
    private static final int NO_EDGE = 1000000000;
    private final List<List<Integer>> adjacency;

    public enum ParticipantType {
        AMBULANCE, PATIENT, HOSPITAL
    }

    public enum Urgency {
        LIGHT(1.00), HEAVY(0.66), CRITICAL(0.33);
        private final double multiplier;
        Urgency(double multiplier) {
            this.multiplier = multiplier;
        }
        public double getMultiplier() {
            return multiplier;
        }
    }

    public PECTOModel(int numAmbulances, int numPatients, int numHospitals) {
        this.numAmbulances = numAmbulances;
        this.numPatients = numPatients;
        this.numHospitals = numHospitals;
        this.hospitalCapacity = (int)((11459 * 0.64) / 64);
        buildDistanceMatrix();
        patientList = new ArrayList<>();
        ambulanceList = new ArrayList<>();
        hospitalList = new ArrayList<>();
        patientUrgency = buildPatientUrgency();
        adjacency = buildAdjacencyMap();
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

    public boolean hasEdge(int from, int to) {
        if (from < 0 || to < 0 || from >= getSize() || to >= getSize()) {
            return false;
        }
        return distanceMatrix[from][to] < NO_EDGE;
    }

    private Urgency[] buildPatientUrgency() {
        Urgency[] urgency = new Urgency[numPatients];
        Random random = new Random();
        Urgency[] values = Urgency.values();
        for (int i = 0; i < numPatients; i++) {
            urgency[i] = values[random.nextInt(3)];
        }
        return urgency;
    }

    public double getPatientUrgency(int patientID) {
        return patientUrgency[patientID - numAmbulances].getMultiplier();
    }

    public List<Integer> getPatientList() {
        return patientList;
    }

    public List<Integer> getHospitalList() {
        return hospitalList;
    }

    public List<Integer> getAmbulanceList() {
        return ambulanceList;
    }

    public int getSize() { return numAmbulances + numPatients + numHospitals; }

    public int getHospitalCapacity() {
        return hospitalCapacity;
    }

    private List<List<Integer>> buildAdjacencyMap() {
        List<List<Integer>> adjacency = new ArrayList<>();

        for (int i = 0; i < getSize(); i++) {
            List<Integer> list = new ArrayList<>();
            list.add(i);
            adjacency.add(list);
        }

        for (Integer a : ambulanceList) {
            for (Integer p : patientList) {
                if (rollDice(0.75)) {
                    adjacency.get(a).add(p);
                }
            }
        }

        for (Integer p : patientList) {
            for (Integer h : hospitalList) {
                if (rollDice(0.75)) {
                    adjacency.get(p).add(h);
                }
            }
        }

        for (Integer h : hospitalList) {
            for (Integer p : patientList) {
                if (rollDice(0.75)) {
                    adjacency.get(h).add(p);
                }
            }
        }

        return adjacency;
    }

    public List<Integer> getAdjacents(int participant) {
        return adjacency.get(participant);
    }

    private void buildDistanceMatrix() {
        int size = getSize();
        distanceMatrix = new int[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    continue;
                }
                distanceMatrix[i][j] = 10 + generateRandomInt(50);
            }
        }
    }

    public int[][] getDistanceMatrix() {
        return distanceMatrix;
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
