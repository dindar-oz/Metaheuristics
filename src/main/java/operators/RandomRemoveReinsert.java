package operators;

import base.MutationOperator;
import representation.IntegerPermutation;
import representation.base.Array;
import representation.base.Representation;
import util.random.RNG;

/**
 * Created by dindar.oz on 28.05.2015.
 */

/**
 *
 *
  */
public class RandomRemoveReinsert<E, S extends Representation<S> & Array<E>> implements MutationOperator<S> {

    @Override
    public S apply(S r, RNG rng) {

        S ni = r.copy();

        int index = rng.randInt(ni.getLength());
        int nextIndex =index;

        while (nextIndex==index)
            nextIndex= rng.randInt(ni.getLength());

        ni.move(index,nextIndex);

        return ni;
    }
}
