package metaheuristic.ea.replacement;

import representation.base.Representation;
import metaheuristic.ea.base.EAContext;
import metaheuristic.ea.base.ReplacementStrategy;
import representation.base.Individual;
import representation.base.Population;

import java.util.List;

/**
 * (mu + lambda): newcomers join the population and the best populationSize individuals survive.
 */
public final class MuPlusLambdaReplacement implements ReplacementStrategy {
    private final boolean rejectDuplicates;

    public MuPlusLambdaReplacement(boolean rejectDuplicates) {
        this.rejectDuplicates = rejectDuplicates;
    }

    public MuPlusLambdaReplacement() {
        this(true);
    }

    @Override
    public <S extends Representation<S>> void replace(Population<S> population, List<Individual<S>> newcomers, EAContext<S> ctx) {
        for (Individual<S> n : newcomers)
            if (!rejectDuplicates || !population.containsRepresentation(n.getRepresentation()))
                population.add(n);

        int excess = population.size() - ctx.populationSize();
        if (excess > 0)
            population.removeAll(population.worst(excess));
    }

    @Override
    public String toString() {
        return "muPlusLambda" + (rejectDuplicates ? "(noDuplicates)" : "");
    }
}
