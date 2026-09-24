package metaheuristic.ea;

import base.MutationOperator;
import base.OptimizationProblem;
import metaheuristic.ea.base.MutationStrategy;
import representation.AgeingIndividual;
import representation.base.Individual;
import representation.base.Population;
import representation.base.Representation;
import util.random.RandUtil;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * With the given probability, replaces mutationCount random individuals with a mutant of themselves.
 */
public class SimpleMutationStrategy implements MutationStrategy {

    private final double mutationProbability;
    private final int mutationCount;
    private boolean allowRepetition = false;

    public SimpleMutationStrategy(double mutationProbability, int mutationCount) {
        this.mutationProbability = mutationProbability;
        this.mutationCount = mutationCount;
    }

    public SimpleMutationStrategy(double mutationProbability) {
        this(mutationProbability, 1);
    }

    public void setAllowRepetition(boolean allowRepetition) {
        this.allowRepetition = allowRepetition;
    }

    @Override
    public void applyMutations(OptimizationProblem problem, Population population, List<MutationOperator> mutationOperators,
                               ToDoubleFunction<Representation> evaluator) {
        if (mutationOperators.isEmpty() || !RandUtil.rollDice(mutationProbability))
            return;

        for (int i = 0; i < mutationCount; i++) {
            Individual victim = population.get(RandUtil.randInt(population.size()));
            MutationOperator mo = mutationOperators.get(RandUtil.randInt(mutationOperators.size()));

            Representation mutantRep = mo.apply(problem, victim.getRepresentation());
            Individual mutant = new AgeingIndividual(mutantRep, evaluator.applyAsDouble(mutantRep));

            if (allowRepetition || !population.contains(mutant)) {
                population.remove(victim);
                population.add(mutant);
            }
        }
    }
}
