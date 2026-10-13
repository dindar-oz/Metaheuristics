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
 * returns a Solution mutated by insert mutation
 * See: Tasgetiren, Pan, Suganthan, Liang, A Discrete DE algorithm for the
 * No-wait Flowshop Scheduling Problem with Total Flowtime Criterion,p.254 2007, (conf)
 * Select two random genes (r1, r2), insert first(r1) after second(r2)
 * 	    1   2
 * X :1,3,2,4,5
 * X':1,2,4,3,5
 * this mutation scheme is applied insertionCount times
 *
  */
public class InsertMutation<E, S extends Representation<S> & Array<E>> implements MutationOperator<S> {

    private final int insertionCount;

    public InsertMutation(int insertionCount) {
        this.insertionCount = insertionCount;
    }

    @Override
    public S apply(S r, RNG rng) {


        S ni = r.copy();
        for (int i =0;i<insertionCount;i++)
        {
            doInsertion(ni, rng);
        }


        return ni;
    }

    private void doInsertion(S ni, RNG rng) {
        int len= ni.getLength();

        int n1 = rng.randInt(len);
        int n2 = rng.randInt(len);

        while (n2 == n1)
            n2 = rng.randInt(len);


        if (n2==(n1-1))
            return; //no change


        if (n1<n2)
        {
            for (int i=n1;i<n2;i++)
            {
                ni.swap(i,i+1);
            }
        }
        else
        {
            for (int i=n1;i>n2;i--)
            {
                ni.swap(i,i-1);
            }
        }
    }

    @Override
    public String toString() {
        return "InsertMutation(" + insertionCount + ")";
    }
}
