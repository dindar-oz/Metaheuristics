package metaheuristic;

import base.OptimizationProblem;
import representation.base.Representation;

/**
 * Provides naming and a describe() built from parameters().
 */
public abstract class AbstractMetaheuristic<S extends Representation<S>, P extends OptimizationProblem<S>>
        implements Metaheuristic<S, P> {

    private final String name;

    protected AbstractMetaheuristic(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    // parameter values as "key=value, ..." for describe()
    protected abstract String parameters();

    @Override
    public String describe() {
        return name + "(" + parameters() + ")";
    }

    @Override
    public String toString() {
        return describe();
    }
}
