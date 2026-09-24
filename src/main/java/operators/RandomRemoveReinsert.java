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
 *
 *
  */
public class RandomRemoveReinsert implements MutationOperator {

    @Override
    public Representation apply(OptimizationProblem problem, Representation r) {
        if (!(r instanceof Array))
            throw new WrongIndividualType("Move can only be applied to permutation");

        Array ni =(Array) r.clone();

        int index = RandUtil.randInt(ni.getLength());
        int nextIndex =index;

        while (nextIndex==index)
            nextIndex= RandUtil.randInt(ni.getLength());

        ni.move(index,nextIndex);

        //System.out.println("index: "+index);
        //System.out.println("nextIndex: " + nextIndex);

        return (Representation)ni;
    }

    @Override
    public int neighboringCount() {
        return 1;
    }
}
