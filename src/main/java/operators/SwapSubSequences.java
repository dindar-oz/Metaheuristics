package operators;

import base.MutationOperator;
import representation.IntegerPermutation;
import representation.base.Array;
import representation.base.Representation;
import util.random.RNG;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public class SwapSubSequences<E, S extends Representation<S> & Array<E>> implements MutationOperator<S> {

    public static final int DEFAULT_SHORTEST_MOVE = 5;
    public static final int DEFAULT_LONGEST_MOVE = 10;

    private final int shortestMoveLength;
    private final int longestMoveLength;
    private final boolean toBeReversed;

    public SwapSubSequences(boolean tbr) {
        this(DEFAULT_SHORTEST_MOVE, DEFAULT_LONGEST_MOVE, tbr);
    }


    public SwapSubSequences(int shortestMoveLength, int longestMoveLength, boolean tbr) {
        this.shortestMoveLength = shortestMoveLength;
        this.longestMoveLength = longestMoveLength;
        this.toBeReversed= tbr;
    }

    @Override
    public S apply(S r, RNG rng) {

        S ni = r.copy();
        List<E> list = ni.getList();

        int leftStartIndex = 0, leftEndIndex, rightStartIndex, rightEndIndex, permutationLength = list.size();

        leftEndIndex = (int) (Math.floor(permutationLength/2.0)-1.0);
        rightStartIndex = leftEndIndex+1;
        rightEndIndex = permutationLength-1;

        int leftSubsequenceLength, rightSubsequenceLength;

        leftSubsequenceLength = shortestMoveLength + rng.randInt(longestMoveLength-shortestMoveLength+1);
        rightSubsequenceLength = shortestMoveLength + rng.randInt(longestMoveLength-shortestMoveLength+1);

        int leftSubsequenceStartIndex = (int)Math.round((leftEndIndex-leftSubsequenceLength+1)*rng.randDouble());
        int rightSubsequenceStartIndex = rightStartIndex + (int)Math.round((rightEndIndex-rightStartIndex+1-rightSubsequenceLength)*rng.randDouble());

        List<E> leftSubsequence = new ArrayList<>(leftSubsequenceLength);
        List<E> rightSubsequence = new ArrayList<>(rightSubsequenceLength);

        if(!toBeReversed){
            for(int i=0; i<leftSubsequenceLength; i++){
                leftSubsequence.add(list.get(leftSubsequenceStartIndex+i));
            }

            for(int i=0; i<rightSubsequenceLength; i++){
                rightSubsequence.add(list.get(rightSubsequenceStartIndex+i));
            }
        }
        else{
            for(int i=0; i<leftSubsequenceLength; i++){
                leftSubsequence.add(list.get(leftSubsequenceStartIndex+leftSubsequenceLength - 1 - i));
            }

            for(int i=0; i<rightSubsequenceLength; i++){
                rightSubsequence.add(list.get(rightSubsequenceStartIndex+rightSubsequenceLength - 1 - i));
            }
        }

        for(int i=0; i<leftSubsequenceLength; i++){
            list.add(rightSubsequenceStartIndex+i, leftSubsequence.get(i));
        }

        for(int i=0; i<rightSubsequenceLength; i++){
            list.remove(rightSubsequenceStartIndex+leftSubsequenceLength);
        }

        for(int i=0; i<rightSubsequenceLength; i++){
            list.add(leftSubsequenceStartIndex+i, rightSubsequence.get(i));
        }

        for(int i=0; i<leftSubsequenceLength; i++){
            list.remove(leftSubsequenceStartIndex+rightSubsequenceLength);
        }

        ni.setList(list);

        return ni;

     }

    @Override
    public String toString() {
        return "SwapSubSequences(" + shortestMoveLength + ".." + longestMoveLength + (toBeReversed ? ", reversed" : "") + ")";
    }
}
