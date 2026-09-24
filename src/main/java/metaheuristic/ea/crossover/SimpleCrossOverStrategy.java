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
public class SimpleCrossOverStrategy implements CrossOverStrategy {


    private double crossOverRate;

    public SimpleCrossOverStrategy(double crossOverRate) {
        this.crossOverRate = crossOverRate;
    }

    @Override
    public List<Individual> generateOffsprings(OptimizationProblem problem, List<Individual> parents, List<CrossOverOperator> crossOverOperators, ToDoubleFunction<Representation> evaluator) {
        List<Individual> offSprings= new ArrayList<Individual>();

        if (RandUtil.rollDice(crossOverRate))
        {
            int offspringCount = parents.size()/2;
            for (int p =0; p<offspringCount;p++)
            {
                CrossOverOperator co = crossOverOperators.get(RandUtil.randInt(crossOverOperators.size()));
                Representation p1 = parents.get(2*p).getRepresentation();
                Representation p2 = parents.get(2*p+1).getRepresentation();
                List<Representation> oList = co.apply(problem,p1, p2);
                for (Representation r : oList) {
                    offSprings.add(new AgeingIndividual(r, evaluator.applyAsDouble(r)));
                }
            }
        }

        return offSprings;
    }
}
