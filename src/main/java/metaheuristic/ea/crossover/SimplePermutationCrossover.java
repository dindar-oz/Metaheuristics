package metaheuristic.ea.crossover;

import base.OptimizationProblem;
import exceptions.WrongIndividualType;
import metaheuristic.ea.base.CrossOverOperator;
import representation.*;
import representation.base.Array;
import representation.base.Representation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */
/**
 * Keeps the first half of the first parent and fills the rest with the missing elements
 * in the order they appear in the second parent. Works for permutations of any element values.
 */
public class SimplePermutationCrossover implements CrossOverOperator {
    @Override
    public List<Representation> apply(OptimizationProblem problem, Representation i1, Representation i2) {

        if (!(i1 instanceof Array) || !(i2 instanceof Array))
            throw new WrongIndividualType("SimplePermutationCrossover can only be applied to permutations");

        Array p1 = (Array)i1;
        Array p2 = (Array)i2;
        Array p3 = (Array)i1.clone();

        int n = p1.getLength();
        int half = n/2;

        Set<Object> kept = new HashSet<>();
        for (int i = 0; i < half; i++)
            kept.add(p1.get(i));

        int pos = half;
        for (int i = 0; i < n; i++)
        {
            Object v = p2.get(i);
            if (!kept.contains(v))
                p3.set(pos++, v);
        }

        List<Representation> offsprings = new ArrayList<Representation>();
        offsprings.add((Representation)p3);

        return offsprings;
    }
}
