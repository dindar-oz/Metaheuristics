package metaheuristic.ea;

import base.MutationOperator;
import metaheuristic.ea.base.MutationStrategy;
import metaheuristic.ea.base.EAContext;
import representation.base.Individual;
import representation.base.Population;
import representation.base.Representation;
import util.random.RNG;

import java.util.List;

/**
 * With the given probability, replaces mutationCount random individuals with a mutant of themselves
 * (a mutant whose representation is already in the population is discarded unless allowRepetition).
 */
public class SimpleMutationStrategy implements MutationStrategy {

    private final double mutationProbability;
    private final int mutationCount;
    private final boolean allowRepetition;

    public SimpleMutationStrategy(double mutationProbability, int mutationCount, boolean allowRepetition) {
        this.mutationProbability = mutationProbability;
        this.mutationCount = mutationCount;
        this.allowRepetition = allowRepetition;
    }

    public SimpleMutationStrategy(double mutationProbability, int mutationCount) {
        this(mutationProbability, mutationCount, false);
    }

    public SimpleMutationStrategy(double mutationProbability) {
        this(mutationProbability, 1);
    }

    @Override
    public <S extends Representation<S>> void applyMutations(Population<S> population, List<MutationOperator<S>> mutationOperators,
                                                             EAContext<S> ctx) {
        RNG rng = ctx.rng();
        if (mutationOperators.isEmpty() || !rng.rollDice(mutationProbability))
            return;

        for (int i = 0; i < mutationCount && !ctx.shouldStop(); i++) {
            Individual<S> victim = population.get(rng.randInt(population.size()));
            MutationOperator<S> mo = mutationOperators.get(rng.randInt(mutationOperators.size()));

            S mutantRep = mo.apply(victim.getRepresentation(), rng);
            Individual<S> mutant = ctx.evaluate(mutantRep);

            if (allowRepetition || !population.containsRepresentation(mutantRep)) {
                population.remove(victim);
                population.add(mutant);
            }
        }
    }

    @Override
    public String toString() {
        return "mutation(p=" + mutationProbability + ", count=" + mutationCount + ")";
    }
}
