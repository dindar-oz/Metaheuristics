package metaheuristic.ea.hook;

import representation.base.Representation;
import base.MutationOperator;
import metaheuristic.ea.base.EAContext;
import metaheuristic.ea.base.GenerationHook;
import representation.base.Individual;
import representation.base.Population;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the operator to the best count individuals and offers the results to the population
 * (through the EA's replacement strategy).
 */
public final class LocalSearchHook<S extends Representation<S>> implements GenerationHook<S> {
    private final MutationOperator<S> operator;
    private final int count;

    public LocalSearchHook(MutationOperator<S> operator, int count) {
        this.operator = operator;
        this.count = count;
    }

    @Override
    public void afterGeneration(Population<S> population, EAContext<S> ctx) {
        List<Individual<S>> improved = new ArrayList<>(count);
        for (Individual<S> i : population.best(count)) {
            if (ctx.shouldStop())
                break;
            improved.add(ctx.evaluate(operator.apply(i.getRepresentation(), ctx.rng())));
        }
        ctx.insert(population, improved);
    }

    @Override
    public String toString() {
        return "localSearch(" + count + ")";
    }
}
