package lab.problemlab.model;

import lab.model.ParamSpec;
import lab.model.ParamValues;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A named, parameterized way to make something in the Problem Lab: an instance generator (from nothing), a solution
 * generator or a mutation operator (from the current instance). The UI edits its parameters with a ParamTable and
 * calls {@link #create} with their values.
 *
 * @param <I> what the component is made from (Void if nothing)
 * @param <T> what it makes
 */
public record ComponentSpec<I, T>(String name, String description, List<ParamSpec> params,
                                  BiFunction<I, ParamValues, T> factory) {

    public ComponentSpec {
        params = List.copyOf(params);
    }

    // a component without parameters
    public static <I, T> ComponentSpec<I, T> of(String name, String description, Function<I, T> factory) {
        return new ComponentSpec<>(name, description, List.of(), (input, values) -> factory.apply(input));
    }

    // a component made from nothing but its parameters, e.g. an instance generator
    public static <T> ComponentSpec<Void, T> generator(String name, String description, List<ParamSpec> params,
                                                       Function<ParamValues, T> factory) {
        return new ComponentSpec<>(name, description, params, (nothing, values) -> factory.apply(values));
    }

    // Throws IllegalArgumentException if the values do not make a valid component
    public T create(I input, ParamValues values) {
        return factory.apply(input, values);
    }

    @Override
    public String toString() {
        return name;    // shown in combo boxes
    }
}
