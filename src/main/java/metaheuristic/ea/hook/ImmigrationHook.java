package metaheuristic.ea.hook;

import representation.base.Representation;
import metaheuristic.ea.base.EAContext;
import metaheuristic.ea.base.GenerationHook;
import representation.base.Population;

/**
 * Every period generations, replaces the worst count individuals with new random ones.
 */
public final class ImmigrationHook<S extends Representation<S>> implements GenerationHook<S> {
    private final int period;
    private final int count;

    public ImmigrationHook(int period, int count) {
        if (period < 1 || count < 1)
            throw new IllegalArgumentException("period and count must be positive");
        this.period = period;
        this.count = count;
    }

    @Override
    public void afterGeneration(Population<S> population, EAContext<S> ctx) {
        if (ctx.generation() % period != 0)
            return;
        int n = Math.min(count, population.size());
        population.removeAll(population.worst(n));
        for (int i = 0; i < n && !ctx.shouldStop(); i++)
            population.add(ctx.createRandom());
    }

    @Override
    public String toString() {
        return "immigration(" + count + " every " + period + ")";
    }
}
