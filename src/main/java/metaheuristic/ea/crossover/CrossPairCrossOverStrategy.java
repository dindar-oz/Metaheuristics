package metaheuristic.ea.crossover;

import metaheuristic.ea.base.CrossOverOperator;
import metaheuristic.ea.base.CrossOverStrategy;
import metaheuristic.ea.base.EAContext;
import representation.base.Individual;
import representation.base.Representation;

import java.util.ArrayList;
import java.util.List;

/**
 * With the given rate, applies every crossover operator to every pair of parents.
 */
public class CrossPairCrossOverStrategy implements CrossOverStrategy {
    private final double crossOverRate;

    public CrossPairCrossOverStrategy(double crossOverRate) {
        this.crossOverRate = crossOverRate;
    }

    @Override
    public <S extends Representation<S>> List<Individual<S>> generateOffsprings(List<Individual<S>> parents,
                                                                             List<CrossOverOperator<S>> crossOverOperators,
                                                                             EAContext<S> ctx) {
        List<Individual<S>> offsprings = new ArrayList<>();
        if (!ctx.rng().rollDice(crossOverRate))
            return offsprings;

        for (CrossOverOperator<S> co : crossOverOperators)
            for (int p1 = 0; p1 < parents.size() - 1; p1++)
                for (int p2 = p1 + 1; p2 < parents.size(); p2++)
                    for (S r : co.apply(parents.get(p1).getRepresentation(), parents.get(p2).getRepresentation(), ctx.rng())) {
                        if (ctx.shouldStop())
                            return offsprings;
                        offsprings.add(ctx.evaluate(r));
                    }
        return offsprings;
    }

    @Override
    public String toString() {
        return "crossPair(rate=" + crossOverRate + ")";
    }
}
