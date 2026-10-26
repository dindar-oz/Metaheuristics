package problem.pecto;

import java.util.ArrayList;
import java.util.List;

public class Hospital implements Participant{
    private final int index;
    private final int capacity;
    private final int location;
    private final List<Patient> patientList;

    public Hospital(int index) {
        this.index = index;
        this.location = index;
        patientList = new ArrayList<>();
        capacity = 14522 / 64;
    }

    public int getCapacity() {
        return capacity;
    }

    public void addPatient(Patient patient) {
        patientList.add(patient);
    }

    public List<Patient> getPatientList() {
        return patientList;
    }

    public boolean isAvailable() {
        return patientList.size() < capacity;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public int getLocation() {
        return location;
    }
}
