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
public class MoveSingleTerm implements MutationOperator {

    public static final int DEFAULT_SHORTEST_MOVE = 5;
    public static final int DEFAULT_LONGEST_MOVE = 10;

    int shortestMoveLength =DEFAULT_SHORTEST_MOVE;
    int longestMoveLength = DEFAULT_LONGEST_MOVE;

    public MoveSingleTerm() {
    }

    public MoveSingleTerm(int shortestMoveLength, int longestMoveLength) {
        this.shortestMoveLength = shortestMoveLength;
        this.longestMoveLength = longestMoveLength;
    }

    @Override
    public Representation apply(OptimizationProblem problem, Representation r) {
        if (!(r instanceof Array))
            throw new WrongIndividualType("Move can only be applied to permutation");

        Array ni =(Array) r.clone();

        int index = RandUtil.randInt(ni.getLength());

        int m = shortestMoveLength + RandUtil.randInt(longestMoveLength-shortestMoveLength+1);

        int nextIndex= (index +m)%ni.getLength();

        ni.move(index,nextIndex);

        //System.out.println("index: "+index);
        //System.out.println("m: " + m);

        return (Representation)ni;
    }

    @Override
    public int neighboringCount() {
        return 1;
    }
}
