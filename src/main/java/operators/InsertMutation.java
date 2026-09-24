package operators;

import base.OptimizationProblem;
import exceptions.WrongIndividualType;
import base.MutationOperator;
import representation.IntegerPermutation;
import representation.base.Array;
import representation.base.Representation;
import util.random.RandUtil;

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
public class InsertMutation implements MutationOperator {

    int insertionCount;

    public InsertMutation(int insertionCount) {
        this.insertionCount = insertionCount;
    }

    @Override
    public Representation apply(OptimizationProblem problem, Representation r) {
        if (!(r instanceof Array) )
            throw new WrongIndividualType("Insert can only be applied to array");


        Array ni =(Array) r.clone();
        for (int i =0;i<insertionCount;i++)
        {
            doInsertion(ni);
        }


        return (Representation)ni;
    }

    @Override
    public int neighboringCount() {
        return 1;
    }

    private void doInsertion(Array ni) {
        int len= ni.getLength();

        int n1 = RandUtil.randInt(len);
        int n2 = RandUtil.randInt(len);

        while (n2 == n1)
            n2 = RandUtil.randInt(len);


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
}
