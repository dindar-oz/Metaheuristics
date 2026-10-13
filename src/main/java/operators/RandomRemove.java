package operators;

import base.MutationOperator;
import representation.IntegerPermutation;
import representation.IntegerVector;
import representation.base.Representation;
import util.random.RNG;

/**
 * Created by dindar.oz on 28.05.2015.
 */

/**
 *
 *
  */
public class RandomRemove implements MutationOperator<IntegerVector> {

    @Override
    public IntegerVector apply(IntegerVector r, RNG rng) {

        IntegerVector ni = r.copy();

        if (ni.getLength()<2)
            return ni;
        int index = rng.randInt(ni.getLength());

        ni.getList().remove(index);

        return ni;
    }
}
