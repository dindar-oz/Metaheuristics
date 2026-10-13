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
public class MoveSubSequence<E, S extends Representation<S> & Array<E>> implements MutationOperator<S> {

    public static final int DEFAULT_SHORTEST_MOVE = 5;
    public static final int DEFAULT_LONGEST_MOVE = 10;

    private final int shortestMoveLength;
    private final int longestMoveLength;

    public MoveSubSequence() {
        this(DEFAULT_SHORTEST_MOVE, DEFAULT_LONGEST_MOVE);
    }

    public MoveSubSequence(int shortestMoveLength, int longestMoveLength) {
        if (shortestMoveLength < 1 || longestMoveLength < shortestMoveLength)
            throw new IllegalArgumentException("need 1 <= shortestMoveLength <= longestMoveLength");
        this.shortestMoveLength = shortestMoveLength;
        this.longestMoveLength = longestMoveLength;
    }


    public static <T> List<T> moveSubSequence(List<T> list, int startIndex, int subsequenceLength, int moveLength)
    {
        T startElement = list.get(startIndex);
        int endIndex = (startIndex + subsequenceLength -1) % list.size();
        T endElement = list.get(endIndex);


        int removedIndex;
        T removedElement, lastElement;

        for (int i=0;i<moveLength;i++)
        {
            endIndex = list.indexOf(endElement);
            removedIndex = (endIndex+1)%list.size();
            removedElement = list.remove(removedIndex);

            startIndex = list.indexOf(startElement);
            list.add(startIndex,removedElement);

            if (endIndex == list.size()-1 || endIndex<startIndex)
            {
                lastElement = list.remove(list.size()-1);
                list.add(0,lastElement);
            }

        }

        return list;
    }

    @Override
    public S apply(S r, RNG rng) {

        S ni = r.copy();

        List<E> list = ni.getList();


        int subsequenceLength = shortestMoveLength + rng.randInt(longestMoveLength-shortestMoveLength+1);
        int moveLength = rng.randInt(ni.getLength());
        int startIndex = rng.randInt(ni.getLength());

        moveSubSequence(list,startIndex,subsequenceLength,moveLength);

        ni.setList(list);

        return ni;

     }

    @Override
    public String toString() {
        return "MoveSubSequence(" + shortestMoveLength + ".." + longestMoveLength + ")";
    }
}
