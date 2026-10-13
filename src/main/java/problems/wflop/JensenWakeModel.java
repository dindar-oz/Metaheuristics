package problems.wflop;

/**
 * The Jensen (Katic, Hojstrup &amp; Jensen 1987) wake model as used by Yu &amp; Zhang (2024):
 * <ul>
 *   <li>a turbine's wake is a cone whose radius grows linearly downstream: r_x = r + alpha x, where x is the
 *       downwind distance, r the rotor radius and alpha = 0.5 / ln(h / z0)
 *       (the paper prints r_x = r + 2 alpha x, which is the growth of the wake DIAMETER in Katic et al.;
 *       r + alpha x is the radius and reproduces the paper's reported results);</li>
 *   <li>the relative velocity deficit inside the wake is a (r / r_x)^2 with a = 1 - sqrt(1 - C_T), Eqs. (1)-(3);</li>
 *   <li>with {@link WakeMode#PARTIAL} it is scaled by the overlap of the wake circle and the rotor disk, Eqs. (5)-(6);</li>
 *   <li>deficits of several upstream turbines combine as the root of the sum of squares, Eq. (4).</li>
 * </ul>
 * Immutable.
 */
public final class JensenWakeModel {

    private final double rotorRadius;
    private final double rotorArea;
    private final double wakeDecay;
    private final double initialDeficit;
    private final WakeMode mode;

    public JensenWakeModel(WindFarmParameters parameters, WakeMode mode) {
        this.rotorRadius = parameters.rotorRadius();
        this.rotorArea = parameters.rotorArea();
        this.wakeDecay = parameters.wakeDecay();
        this.initialDeficit = parameters.initialDeficit();
        this.mode = mode;
    }

    public WakeMode mode() {
        return mode;
    }

    // Wake radius at downwind distance x, Eq. (3)
    public double wakeRadius(double x) {
        return rotorRadius + wakeDecay * x;
    }

    /**
     * Relative velocity deficit (1 - v / v0) that an upstream turbine causes at a downstream turbine.
     *
     * @param dx       downstream x minus upstream x [m]
     * @param dy       downstream y minus upstream y [m]
     * @param flowX    x component of the unit vector the wind blows to
     * @param flowY    y component of the unit vector the wind blows to
     */
    public double deficit(double dx, double dy, double flowX, double flowY) {
        double x = dx * flowX + dy * flowY;              // downwind distance
        if (x <= 0)
            return 0;                                     // not downstream
        double lateral = Math.abs(dx * flowY - dy * flowX);
        double rx = wakeRadius(x);
        if (lateral >= rx + rotorRadius)
            return 0;                                     // the wake misses the rotor
        double ratio = rotorRadius / rx;
        double full = initialDeficit * ratio * ratio;     // Eq. (1): a (r / r_x)^2
        if (mode == WakeMode.FULL)
            return full;
        return full * circleOverlap(lateral, rx, rotorRadius) / rotorArea;   // Eq. (6)
    }

    // Eq. (4): combined relative deficit from the sum of squared single deficits
    public static double combine(double sumOfSquaredDeficits) {
        return Math.sqrt(sumOfSquaredDeficits);
    }

    // v / v0 of a turbine with the given sum of squared deficits, Eq. (4); never negative
    public static double speedRatio(double sumOfSquaredDeficits) {
        return Math.max(0, 1 - combine(sumOfSquaredDeficits));
    }

    /**
     * Area of the intersection of two circles with radii r1, r2 whose centers are d apart, Eq. (5)
     * (Weisstein, "Circle-Circle Intersection"), extended to the no-overlap and containment cases.
     */
    public static double circleOverlap(double d, double r1, double r2) {
        if (d >= r1 + r2)
            return 0;
        if (d <= Math.abs(r1 - r2))
            return Math.PI * Math.min(r1, r2) * Math.min(r1, r2);
        double a1 = r1 * r1 * Math.acos((d * d + r1 * r1 - r2 * r2) / (2 * d * r1));
        double a2 = r2 * r2 * Math.acos((d * d + r2 * r2 - r1 * r1) / (2 * d * r2));
        double a3 = 0.5 * Math.sqrt(Math.max(0, (-d + r1 + r2) * (d + r1 - r2) * (d - r1 + r2) * (d + r1 + r2)));
        return a1 + a2 - a3;
    }

    @Override
    public String toString() {
        return String.format("Jensen(%s, alpha=%.5f, a=%.5f)", mode, wakeDecay, initialDeficit);
    }
}
