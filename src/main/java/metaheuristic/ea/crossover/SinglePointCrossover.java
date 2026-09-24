package metaheuristic.ea.crossover;

import base.OptimizationProblem;
import exceptions.WrongIndividualType;
import metaheuristic.ea.base.CrossOverOperator;
import representation.IntegerVector;
import representation.base.Array;
import representation.base.Representation;
import util.random.RandUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */

/**
 * crossover operator written for PSO
 * See: Pan, tasgetiren, Liang, A Discrete PSO algorithm for the no-wait
 * flowshop scheduling problem, p.2811, 2008, (journal)
 * randomly selects a cut point, r1
 * gets the elements up to r1 from this permutation, the rest are taken from
 * Solution V in the same order, taking care of repetition
 * The reverse is done with 0.5 prob.
 * example, assume r1=2
 * P1:5,1,4,2,3
 * P2:3,5,4,2,1
 * O1:5,1,3,4,2
 * O2:3,5,1,4,2
 */
public class SinglePointCrossover implements CrossOverOperator {
    @Override
    public List<Representation> apply(OptimizationProblem problem, Representation i1, Representation i2) {

        if (!(i1 instanceof Array) || !(i2 instanceof Array))
            throw new WrongIndividualType("Swap can only be applied to permutation");



        Array p1 = (Array)i1;
        Array p2 = (Array)i2;


        int n = Math.max(p1.getLength(),p2.getLength());
        int cutPoint = RandUtil.randInt(n);

        List<Object> childList = new ArrayList<>();

        for (int i = 0; i < cutPoint && i<p1.getLength() ; i++) {
            childList.add(p1.get(i));
        }

        for (int i = cutPoint; i<p2.getLength() ; i++) {
            if(!childList.contains(p2.get(i)))
                childList.add(p2.get(i));
        }

        Array child = ((Array) i1).cloneArray();
        child.setList(childList);

        childList = new ArrayList<>();
        for (int i = 0; i < cutPoint && i<p2.getLength() ; i++) {
            childList.add(p2.get(i));
        }

        for (int i = cutPoint; i<p1.getLength() ; i++) {
            if(!childList.contains(p1.get(i)))
                childList.add(p1.get(i));
        }

        Array child2 = ((Array) i1).cloneArray();
        child2.setList(childList);

        List<Representation> offsprings = new ArrayList<Representation>();
        offsprings.add((Representation)child);
        offsprings.add((Representation)child2);

        return offsprings;
    }
}
