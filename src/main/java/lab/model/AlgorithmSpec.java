package lab.model;

import base.OptimizationProblem;
import metaheuristic.Metaheuristic;
import representation.base.Representation;

import java.util.List;
import java.util.function.BiFunction;

/**
 * An algorithm offered for problems of type P: its editable parameters and how to build it from their values.
 */
public final class AlgorithmSpec<S extends Representation<S>, P extends OptimizationProblem<S>> {
    private final String name;
    private final List<ParamSpec> params;
    private final BiFunction<P, ParamValues, Metaheuristic<S, ? super P>> factory;

    public AlgorithmSpec(String name, List<ParamSpec> params, BiFunction<P, ParamValues, Metaheuristic<S, ? super P>> factory) {
        this.name = name;
        this.params = params;
        this.factory = factory;
    }

    public String name() {
        return name;
    }

    public List<ParamSpec> params() {
        return params;
    }

    public Metaheuristic<S, ? super P> create(P problem, ParamValues values) {
        return factory.apply(problem, values);
    }
}
