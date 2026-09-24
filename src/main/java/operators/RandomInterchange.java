package operators;

import base.OptimizationProblem;
import exceptions.WrongIndividualType;
import base.MutationOperator;
import representation.base.Array;
import representation.base.Representation;
import util.random.RandUtil;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public class RandomInterchange implements MutationOperator {
    @Override
    public Representation apply(OptimizationProblem problem, Representation r) {
        if (!(r instanceof Array))
            throw new WrongIndividualType("Swap can only be applied to permutation");

        Array p = (Array)r;

        int n1 = RandUtil.randInt(p.getLength());
        int n2 = RandUtil.randInt(p.getLength());

        while (n2 == n1)
            n2 = RandUtil.randInt(p.getLength());

        Array ni =(Array) r.clone();

        ni.swap(n1,n2);


        return (Representation)ni;
    }

    @Override
    public int neighboringCount() {
        return 1;
    }
}
