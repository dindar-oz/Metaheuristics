package operators;

import base.MutationOperator;
import representation.IntegerPermutation;
import representation.base.Array;
import representation.base.Representation;
import util.random.RNG;

import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public class ReverseSubSequence<E, S extends Representation<S> & Array<E>> implements MutationOperator<S> {

    public static final int DEFAULT_SHORTEST_MOVE = 5;
    public static final int DEFAULT_LONGEST_MOVE = 10;

    private final int shortestMoveLength;
    private final int longestMoveLength;

    public ReverseSubSequence() {
        this(DEFAULT_SHORTEST_MOVE, DEFAULT_LONGEST_MOVE);
    }

    public ReverseSubSequence(int shortestMoveLength, int longestMoveLength) {
        if (shortestMoveLength < 1 || longestMoveLength < shortestMoveLength)
            throw new IllegalArgumentException("need 1 <= shortestMoveLength <= longestMoveLength");
        this.shortestMoveLength = shortestMoveLength;
        this.longestMoveLength = longestMoveLength;
    }

    public static <T> List<T> reverseSubSequence(List<T> list, int startIndex, int subsequenceLength)
    {
        int endIndex = (startIndex + subsequenceLength -1) % list.size();
        T temp;

        for (int i=0;i<subsequenceLength/2;i++)
        {
            temp = list.get(startIndex);
            list.set(startIndex,list.get(endIndex));
            list.set(endIndex,temp);
            startIndex = (startIndex+1)%list.size();
            endIndex   = (endIndex-1+list.size())%list.size();
        }
        return list;
    }


    @Override
    public S apply(S r, RNG rng) {

        S ni = r.copy();

        List<E> list = ni.getList();

        int subsequenceLength = shortestMoveLength + rng.randInt(longestMoveLength-shortestMoveLength+1);


        int startIndex = rng.randInt(ni.getLength());

        reverseSubSequence(list,startIndex,subsequenceLength);

        ni.setList(list);
        return ni;


     }

    @Override
    public String toString() {
        return "ReverseSubSequence(" + shortestMoveLength + ".." + longestMoveLength + ")";
    }
}
