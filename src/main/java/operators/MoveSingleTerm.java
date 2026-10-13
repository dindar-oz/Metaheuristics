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
public class MoveSingleTerm<E, S extends Representation<S> & Array<E>> implements MutationOperator<S> {

    public static final int DEFAULT_SHORTEST_MOVE = 5;
    public static final int DEFAULT_LONGEST_MOVE = 10;

    private final int shortestMoveLength;
    private final int longestMoveLength;

    public MoveSingleTerm() {
        this(DEFAULT_SHORTEST_MOVE, DEFAULT_LONGEST_MOVE);
    }

    public MoveSingleTerm(int shortestMoveLength, int longestMoveLength) {
        if (shortestMoveLength < 1 || longestMoveLength < shortestMoveLength)
            throw new IllegalArgumentException("need 1 <= shortestMoveLength <= longestMoveLength");
        this.shortestMoveLength = shortestMoveLength;
        this.longestMoveLength = longestMoveLength;
    }

    @Override
    public S apply(S r, RNG rng) {

        S ni = r.copy();

        int index = rng.randInt(ni.getLength());

        int m = shortestMoveLength + rng.randInt(longestMoveLength-shortestMoveLength+1);

        int nextIndex= (index +m)%ni.getLength();

        ni.move(index,nextIndex);

        return ni;
    }

    @Override
    public String toString() {
        return "MoveSingleTerm(" + shortestMoveLength + ".." + longestMoveLength + ")";
    }
}
