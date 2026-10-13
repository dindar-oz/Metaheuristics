package problems.pecto;

/**
 * The urgency level U_p of a patient, with the weights of Eq. (7). A leg of a route costs its distance times the
 * weight of the patient it involves (Eq. 8), so legs to and from more urgent patients cost less.
 */
public enum Urgency {

    STABLE(1.00),
    LIGHT(0.80),
    MODERATE(0.60),
    HEAVY(0.40),
    CRITICAL(0.20);

    private final double weight;

    Urgency(double weight) {
        this.weight = weight;
    }

    // w(U_p)
    public double weight() {
        return weight;
    }
}
