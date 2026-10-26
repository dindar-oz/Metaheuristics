package problems.pecto.aco.gui;

import problems.pecto.PECTOProblem;
import problems.pecto.Urgency;

import java.awt.Color;
import java.util.Locale;

/**
 * Colors, labels and number formats shared by the diagnosis' views.
 */
final class Style {

    static final Color HOSPITAL = new Color(0xD0312D);
    static final Color BEST = new Color(150, 158, 168);
    static final Color PHEROMONE = new Color(0x1F6FEB);
    static final Color SELECTION = new Color(0x111111);
    static final Color HIGHLIGHT = new Color(0xFFB300);
    static final Color MUTED = new Color(110, 118, 128);

    private Style() {
    }

    // a distinct color for every ambulance (golden-ratio hues)
    static Color ambulance(int a) {
        float hue = (float) ((a * 0.61803398875) % 1.0);
        return Color.getHSBColor(hue, 0.70f, 0.78f);
    }

    static Color urgency(Urgency u) {
        return switch (u) {
            case CRITICAL -> new Color(0xC2185B);
            case HEAVY -> new Color(0xE0532F);
            case MODERATE -> new Color(0xD98200);
            case LIGHT -> new Color(0x7FA31E);
            case STABLE -> new Color(0x2F7D32);
        };
    }

    // A3, P12, H40: the type and the participant ID
    static String label(PECTOProblem p, int id) {
        if (id < 0)
            return "–";
        return (p.isAmbulance(id) ? "A" : p.isPatient(id) ? "P" : "H") + id;
    }

    static String num(double x) {
        if (Double.isNaN(x))
            return "–";
        if (Double.isInfinite(x))
            return x > 0 ? "∞" : "−∞";
        double a = Math.abs(x);
        if (a == 0)
            return "0";
        if (a >= 1000)
            return String.format(Locale.ROOT, "%,.1f", x);
        if (a >= 1)
            return String.format(Locale.ROOT, "%.3f", x);
        if (a >= 0.001)
            return String.format(Locale.ROOT, "%.4f", x);
        return String.format(Locale.ROOT, "%.3e", x);
    }

    static String pct(double share) {
        if (Double.isNaN(share))
            return "–";
        return String.format(Locale.ROOT, share < 0.001 && share > 0 ? "%.3f%%" : "%.1f%%", 100 * share);
    }

    static String state(byte s) {
        return DiagnosticAnt.STATE_NAMES[s];
    }

    static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
