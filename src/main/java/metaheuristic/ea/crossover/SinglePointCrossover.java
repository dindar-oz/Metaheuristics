package metaheuristic.ea.crossover;

import metaheuristic.ea.base.CrossOverOperator;
import representation.IntegerVector;
import representation.base.Array;
import representation.base.Representation;
import util.random.RNG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Child = first parent's prefix up to a random cut + the second parent's suffix, skipping elements already present
 * (and vice versa for the second child). Meant for variable-length sequences: on permutations the children
 * can be SHORTER than the parents, so use SimplePermutationCrossover/OneCutCrossover/PTLCutCrossover there.
 */
public class SinglePointCrossover<E, S extends Representation<S> & Array<E>> implements CrossOverOperator<S> {
    @Override
    public List<S> apply(S i1, S i2, RNG rng) {
        S p1 = i1;
        S p2 = i2;


        int n = Math.max(p1.getLength(),p2.getLength());
        int cutPoint = rng.randInt(n);

        List<E> childList = new ArrayList<>();

        for (int i = 0; i < cutPoint && i<p1.getLength() ; i++) {
            childList.add(p1.get(i));
        }

        for (int i = cutPoint; i<p2.getLength() ; i++) {
            if(!childList.contains(p2.get(i)))
                childList.add(p2.get(i));
        }

        S child = i1.copy();
        child.setList(childList);

        childList = new ArrayList<>();
        for (int i = 0; i < cutPoint && i<p2.getLength() ; i++) {
            childList.add(p2.get(i));
        }

        for (int i = cutPoint; i<p1.getLength() ; i++) {
            if(!childList.contains(p1.get(i)))
                childList.add(p1.get(i));
        }

        S child2 = i1.copy();
        child2.setList(childList);

        List<S> offsprings = new ArrayList<>();
        offsprings.add(child);
        offsprings.add(child2);

        return offsprings;
    }
}
