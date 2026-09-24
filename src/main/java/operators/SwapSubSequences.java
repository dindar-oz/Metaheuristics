package operators;

import base.OptimizationProblem;
import base.MutationOperator;
import representation.IntegerPermutation;
import representation.base.Array;
import representation.base.Representation;
import util.random.RandUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public class SwapSubSequences implements MutationOperator {

    public static final int DEFAULT_SHORTEST_MOVE = 5;
    public static final int DEFAULT_LONGEST_MOVE = 10;

    int shortestMoveLength =DEFAULT_SHORTEST_MOVE;
    int longestMoveLength = DEFAULT_LONGEST_MOVE;
    boolean toBeReversed = false;

    public SwapSubSequences(boolean tbr) {
        toBeReversed = tbr;
    }


    public SwapSubSequences(int shortestMoveLength, int longestMoveLength, boolean tbr) {
        this.shortestMoveLength = shortestMoveLength;
        this.longestMoveLength = longestMoveLength;
        this.toBeReversed= tbr;
    }

    @Override
    public Representation apply(OptimizationProblem problem, Representation r) {

        Array ni = (Array) r.clone();
        List list = ni.getList();

        int leftStartIndex = 0, leftEndIndex, rightStartIndex, rightEndIndex, permutationLength = list.size();

        leftEndIndex = (int) (Math.floor(permutationLength/2.0)-1.0);
        rightStartIndex = leftEndIndex+1;
        rightEndIndex = permutationLength-1;

        int leftSubsequenceLength, rightSubsequenceLength;

        leftSubsequenceLength = shortestMoveLength + RandUtil.randInt(longestMoveLength-shortestMoveLength+1);
        rightSubsequenceLength = shortestMoveLength + RandUtil.randInt(longestMoveLength-shortestMoveLength+1);

        int leftSubsequenceStartIndex = (int)Math.round((leftEndIndex-leftSubsequenceLength+1)*Math.random());
        int rightSubsequenceStartIndex = rightStartIndex + (int)Math.round((rightEndIndex-rightStartIndex+1-rightSubsequenceLength)*Math.random());

        ArrayList<Object> leftSubsequence = new ArrayList(leftSubsequenceLength);
        ArrayList<Object> rightSubsequence = new ArrayList(rightSubsequenceLength);

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

        return (Representation) ni;

     }

    @Override
    public int neighboringCount() {
        return 1;
    }
}
