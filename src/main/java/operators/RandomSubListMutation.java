package operators;

import base.MutationOperator;
import representation.IntegerPermutation;
import representation.base.Representation;
import util.random.RNG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by dindar.oz on 10.06.2015.
 */
public abstract class RandomSubListMutation implements MutationOperator<IntegerPermutation> {

    @Override
    public IntegerPermutation apply(IntegerPermutation r, RNG rng) {
        IntegerPermutation ip = r;

        IntegerPermutation np = ip.copy();

        int len = ip.getLength();
        if (len<2)
            return np;


        int index = (len== 2) ? 0: rng.randInt(len - 2);

        int l = 2 + rng.randInt(len-index-2);

        Integer[] sub = Arrays.copyOfRange(np.getValues(), index, index + l);

        List<Integer> subList = new ArrayList<>(Arrays.asList(sub));

        mutateSubList(subList);

        for (int i=0;i<l;i++)
        {
            np.getValues()[i+index]= subList.get(i);
        }

        return np;
    }

    protected abstract void mutateSubList(List<Integer> subList);
}
