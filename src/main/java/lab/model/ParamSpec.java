package lab.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Describes one editable parameter of a problem or an algorithm: its type, default value and valid range.
 */
public final class ParamSpec {

    public enum Kind { INT, DOUBLE, CHOICE }

    private final String name;
    private final Kind kind;
    private final Object defaultValue;
    private final double min;
    private final double max;
    private final List<String> choices;
    private final String description;

    private ParamSpec(String name, Kind kind, Object defaultValue, double min, double max, List<String> choices, String description) {
        this.name = name;
        this.kind = kind;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.choices = choices;
        this.description = description;
    }

    public static ParamSpec integer(String name, int defaultValue, int min, int max, String description) {
        return new ParamSpec(name, Kind.INT, defaultValue, min, max, Collections.<String>emptyList(), description);
    }

    public static ParamSpec real(String name, double defaultValue, double min, double max, String description) {
        return new ParamSpec(name, Kind.DOUBLE, defaultValue, min, max, Collections.<String>emptyList(), description);
    }

    public static ParamSpec choice(String name, String defaultValue, String description, String... choices) {
        return new ParamSpec(name, Kind.CHOICE, defaultValue, 0, 0, Arrays.asList(choices), description);
    }

    public String name() {
        return name;
    }

    public Kind kind() {
        return kind;
    }

    public Object defaultValue() {
        return defaultValue;
    }

    public List<String> choices() {
        return choices;
    }

    public String description() {
        return description;
    }

    // Converts and validates user input; throws IllegalArgumentException with a readable message
    public Object parse(String text) {
        String t = text.trim();
        try {
            switch (kind) {
                case INT: {
                    int v = Integer.parseInt(t);
                    if (v < min || v > max)
                        throw new IllegalArgumentException(name + " must be between " + (long) min + " and " + (long) max);
                    return v;
                }
                case DOUBLE: {
                    double v = Double.parseDouble(t);
                    if (Double.isNaN(v) || v < min || v > max)
                        throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
                    return v;
                }
                default:
                    if (!choices.contains(t))
                        throw new IllegalArgumentException(name + " must be one of " + choices);
                    return t;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + ": '" + text + "' is not a valid " + (kind == Kind.INT ? "integer" : "number"));
        }
    }
}
