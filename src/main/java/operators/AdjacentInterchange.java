package operators;

import base.MutationOperator;
import representation.base.Array;
import representation.base.Representation;
import util.random.RNG;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public class AdjacentInterchange<E, S extends Representation<S> & Array<E>> implements MutationOperator<S> {
    @Override
    public S apply(S r, RNG rng) {

        S p = r;

        int n1 = rng.randInt(p.getLength());
        int n2 = (n1+1)%p.getLength();

        S ni = r.copy();

        ni.swap(n1,n2);


        return ni;
    }
}
