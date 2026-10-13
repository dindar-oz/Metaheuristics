package metaheuristic.ea.replacement;

import representation.base.Representation;
import metaheuristic.ea.base.EAContext;
import metaheuristic.ea.base.ReplacementStrategy;
import metaheuristic.ea.base.VictimSelector;
import representation.base.Individual;
import representation.base.Population;

import java.util.ArrayList;
import java.util.List;

/**
 * Newcomers always enter the population; the members they replace are chosen from the old population by the
 * victim selector. Newcomers whose representation is already present are rejected if rejectDuplicates is set.
 */
public final class SteadyStateReplacement implements ReplacementStrategy {
    private final VictimSelector victimSelector;
    private final boolean rejectDuplicates;

    public SteadyStateReplacement(VictimSelector victimSelector, boolean rejectDuplicates) {
        this.victimSelector = victimSelector;
        this.rejectDuplicates = rejectDuplicates;
    }

    public SteadyStateReplacement(VictimSelector victimSelector) {
        this(victimSelector, true);
    }

    @Override
    public <S extends Representation<S>> void replace(Population<S> population, List<Individual<S>> newcomers, EAContext<S> ctx) {
        List<Individual<S>> accepted = new ArrayList<>(newcomers.size());
        for (Individual<S> n : newcomers)
            if (!rejectDuplicates || (!population.containsRepresentation(n.getRepresentation()) && !containsRepresentation(accepted, n)))
                accepted.add(n);

        int excess = population.size() + accepted.size() - ctx.populationSize();
        if (excess > 0)
            population.removeAll(victimSelector.selectVictims(population.individuals(), Math.min(excess, population.size()), ctx.rng()));
        population.addAll(accepted);

        if (population.size() > ctx.populationSize()) // more newcomers than places
            population.removeAll(population.worst(population.size() - ctx.populationSize()));
    }

    private static <S extends Representation<S>> boolean containsRepresentation(List<Individual<S>> list, Individual<S> individual) {
        for (Individual<S> i : list)
            if (i.getRepresentation().equals(individual.getRepresentation()))
                return true;
        return false;
    }

    @Override
    public String toString() {
        return "steadyState(victims=" + victimSelector + (rejectDuplicates ? ", noDuplicates" : "") + ")";
    }
}
