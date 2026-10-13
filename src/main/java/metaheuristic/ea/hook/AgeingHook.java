package metaheuristic.ea.hook;

import representation.base.Representation;
import metaheuristic.ea.base.EAContext;
import metaheuristic.ea.base.GenerationHook;
import representation.base.Population;

/**
 * Removes individuals older than maxAge generations and refills the population with new random ones.
 */
public final class AgeingHook<S extends Representation<S>> implements GenerationHook<S> {
    private final int maxAge;

    public AgeingHook(int maxAge) {
        if (maxAge < 1)
            throw new IllegalArgumentException("maxAge must be positive");
        this.maxAge = maxAge;
    }

    @Override
    public void afterGeneration(Population<S> population, EAContext<S> ctx) {
        population.removeIf(i -> i.getAge(ctx.generation()) > maxAge);
        while (population.size() < ctx.populationSize() && !ctx.shouldStop())
            population.add(ctx.createRandom());
    }

    @Override
    public String toString() {
        return "ageing(" + maxAge + ")";
    }
}
