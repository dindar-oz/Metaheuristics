package metaheuristic.ea.crossover;

import base.OptimizationProblem;
import exceptions.WrongIndividualType;
import metaheuristic.ea.base.CrossOverOperator;
import representation.IntegerPermutation;
import representation.IntegerVector;
import representation.base.Array;
import representation.base.Representation;
import util.random.RandUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
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
public class OneCutCrossover implements CrossOverOperator {
    @Override
    public List<Representation> apply(OptimizationProblem problem, Representation i1, Representation i2) {

        if (!(i1 instanceof Array) || !(i2 instanceof Array))
            throw new WrongIndividualType("Swap can only be applied to permutation");



        Array p1 = (Array)i1;
        Array p2 = (Array)i2;
        boolean change= RandUtil.rollDice(0.5);

        if (change)
        {
            p1= p2;
            p2 = (Array)i1;
        }

        int n = p1.getLength();
        int cutPoint = RandUtil.randInt(n);

        Array p3 = (Array)p1.cloneArray();

        if (cutPoint!=p1.getLength()-1 && cutPoint !=0 && cutPoint<p2.getLength()) { // no change otherwise
            // positions after the cut take p2's element if it is not used yet, the rest are left empty
            Set<Object> used = new HashSet<>();
            for (int i = 0; i < cutPoint; i++)
                used.add(p1.get(i));

            Object[] tail = new Object[n - cutPoint];
            for (int i = cutPoint; i < n; i++) {
                if (used.add(p2.get(i)))
                    tail[i - cutPoint] = p2.get(i);
            }

            // empty positions are filled with the missing elements in p2's order
            int j = 0;
            for (int i = 0; i < n; i++) {
                Object v = p2.get(i);
                if (used.add(v)) {
                    while (tail[j] != null)
                        j++;
                    tail[j] = v;
                }
            }

            for (int i = cutPoint; i < n; i++)
                p3.set(i, tail[i - cutPoint]);
        }

        List<Representation> offsprings = new ArrayList<Representation>();
        offsprings.add((Representation)p3);

        return offsprings;
    }
}
