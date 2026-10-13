package metaheuristic.ea.crossover;

import util.random.RNG;
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
public class SimplePermutationCrossover<E, S extends Representation<S> & Array<E>> implements CrossOverOperator<S> {
    @Override
    public List<S> apply(S i1, S i2, RNG rng) {

        S p1 = i1;
        S p2 = i2;
        S p3 = i1.copy();

        int n = p1.getLength();
        int half = n/2;

        Set<E> kept = new HashSet<>();
        for (int i = 0; i < half; i++)
            kept.add(p1.get(i));

        int pos = half;
        for (int i = 0; i < n; i++)
        {
            E v = p2.get(i);
            if (!kept.contains(v))
                p3.set(pos++, v);
        }

        List<S> offsprings = new ArrayList<>();
        offsprings.add(p3);

        return offsprings;
    }
}
