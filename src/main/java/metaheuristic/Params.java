package metaheuristic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Parameter validation and formatting helpers for algorithm builders.
 */
public final class Params {

    private Params() {
    }

    public static <T> T required(String name, T value) {
        if (value == null)
            throw new IllegalArgumentException(name + " is required");
        return value;
    }

    public static int positive(String name, int value) {
        if (value <= 0)
            throw new IllegalArgumentException(name + " must be positive, was " + value);
        return value;
    }

    public static double positive(String name, double value) {
        if (!(value > 0) || Double.isInfinite(value))
            throw new IllegalArgumentException(name + " must be a positive number, was " + value);
        return value;
    }

    public static double nonNegative(String name, double value) {
        if (!(value >= 0) || Double.isInfinite(value))
            throw new IllegalArgumentException(name + " must be a non-negative number, was " + value);
        return value;
    }

    public static double probability(String name, double value) {
        if (!(value >= 0 && value <= 1))
            throw new IllegalArgumentException(name + " must be in [0, 1], was " + value);
        return value;
    }

    public static int nonNegative(String name, int value) {
        if (value < 0)
            throw new IllegalArgumentException(name + " must not be negative, was " + value);
        return value;
    }

    // min <= value <= max
    public static int between(String name, int value, int min, int max) {
        if (value < min || value > max)
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ", was " + value);
        return value;
    }

    // value < limit (strictly); combine with positive or nonNegative for the lower end
    public static double below(String name, double value, double limit) {
        if (!(value < limit))
            throw new IllegalArgumentException(name + " must be below " + limit + ", was " + value);
        return value;
    }

    public static <T> List<T> immutableCopy(Collection<? extends T> values) {
        return values == null ? Collections.<T>emptyList() : Collections.unmodifiableList(new ArrayList<T>(values));
    }

    public static <T> List<T> nonEmpty(String name, Collection<? extends T> values) {
        if (values == null || values.isEmpty())
            throw new IllegalArgumentException(name + " must not be empty");
        return immutableCopy(values);
    }

    // [a, b, c] using toString(), or the simple class name when toString() is not overridden
    public static String describe(Collection<?> values) {
        return values.stream().map(Params::describe).collect(Collectors.joining(", ", "[", "]"));
    }

    public static String describe(Object value) {
        if (value == null)
            return "null";
        String s = value.toString();
        String identity = value.getClass().getName() + "@";
        return s.startsWith(identity) || s.contains("$$Lambda") ? value.getClass().getSimpleName() : s;
    }
}
