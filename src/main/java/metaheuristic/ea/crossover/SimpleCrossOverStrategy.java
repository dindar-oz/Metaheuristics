package metaheuristic.ea.crossover;

import metaheuristic.ea.base.CrossOverOperator;
import metaheuristic.ea.base.CrossOverStrategy;
import metaheuristic.ea.base.EAContext;
import representation.base.Individual;
import representation.base.Representation;
import util.random.RNG;

import java.util.ArrayList;
import java.util.List;

/**
 * With the given rate, pairs up consecutive parents and applies a random crossover operator to each pair.
 */
public class SimpleCrossOverStrategy implements CrossOverStrategy {
    private final double crossOverRate;

    public SimpleCrossOverStrategy(double crossOverRate) {
        this.crossOverRate = crossOverRate;
    }

    @Override
    public <S extends Representation<S>> List<Individual<S>> generateOffsprings(List<Individual<S>> parents,
                                                                             List<CrossOverOperator<S>> crossOverOperators,
                                                                             EAContext<S> ctx) {
        RNG rng = ctx.rng();
        List<Individual<S>> offsprings = new ArrayList<>();
        if (!rng.rollDice(crossOverRate))
            return offsprings;

        for (int p = 0; p + 1 < parents.size(); p += 2) {
            CrossOverOperator<S> co = crossOverOperators.get(rng.randInt(crossOverOperators.size()));
            for (S r : co.apply(parents.get(p).getRepresentation(), parents.get(p + 1).getRepresentation(), rng)) {
                if (ctx.shouldStop())
                    return offsprings;
                offsprings.add(ctx.evaluate(r));
            }
        }
        return offsprings;
    }

    @Override
    public String toString() {
        return "crossover(rate=" + crossOverRate + ")";
    }
}
