package metaheuristic.ea.crossover;

import base.OptimizationProblem;
import metaheuristic.ea.base.CrossOverOperator;
import metaheuristic.ea.base.CrossOverStrategy;
import representation.AgeingIndividual;
import representation.SimpleIndividual;
import representation.base.Individual;
import representation.base.Representation;
import util.random.RandUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Created by dindar.oz on 11.06.2015.
 */
public class CrossPairCrossOverStrategy implements CrossOverStrategy {


    private double crossOverRate;

    public CrossPairCrossOverStrategy(double crossOverRate) {
        this.crossOverRate = crossOverRate;
    }

    @Override
    public List<Individual> generateOffsprings(OptimizationProblem problem, List<Individual> parents, List<CrossOverOperator> crossOverOperators, ToDoubleFunction<Representation> evaluator) {
        List<Individual> offSprings= new ArrayList<Individual>();

        if (RandUtil.rollDice(crossOverRate)) {
            for (CrossOverOperator co : crossOverOperators) {
                for (int p1 = 0; p1 < parents.size() - 1; p1++) {
                    for (int p2 = p1 + 1; p2 < parents.size(); p2++) {
                        List<Representation> oList = co.apply(problem, parents.get(p1).getRepresentation(), parents.get(p2).getRepresentation());
                        for (Representation r : oList) {
                            offSprings.add(new AgeingIndividual(r, evaluator.applyAsDouble(r)));
                        }
                    }
                }
            }
        }
        return offSprings;
    }
}
