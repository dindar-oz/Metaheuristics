package problem.pecto;

import java.util.Random;

public class Patient implements Participant{
    public enum Urgency {
        CRITICAL, HEAVY, LIGHT
    }

    private final int index;
    private final int location;
    private Urgency urgency;

    public Patient(int index) {
        this.index = index;
        this.location = index;
        assignPatientUrgency();
    }

    private void assignPatientUrgency() {
        Random random = new Random();
        int value = random.nextInt(3);
        urgency = Urgency.values()[value];
    }

    public double getUrgencyWeight() {
        switch (urgency) {
            case HEAVY -> {return 0.66;}
            case CRITICAL -> {return 0.33;}
            default -> {return 1.0;}
        }
    }

    public Urgency getUrgency() {
        return urgency;
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
