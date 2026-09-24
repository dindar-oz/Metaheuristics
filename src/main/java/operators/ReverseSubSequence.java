package operators;

import base.OptimizationProblem;
import base.MutationOperator;
import representation.IntegerPermutation;
import representation.base.Array;
import representation.base.Representation;
import util.random.RandUtil;

import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public class ReverseSubSequence implements MutationOperator {

    public static final int DEFAULT_SHORTEST_MOVE = 5;
    public static final int DEFAULT_LONGEST_MOVE = 10;

    int shortestMoveLength =DEFAULT_SHORTEST_MOVE;
    int longestMoveLength = DEFAULT_LONGEST_MOVE;

    public ReverseSubSequence() {
    }

    public ReverseSubSequence(int shortestMoveLength, int longestMoveLength) {
        this.shortestMoveLength = shortestMoveLength;
        this.longestMoveLength = longestMoveLength;
    }

    public static List reverseSubSequence(List list, int startIndex, int subsequenceLength)
    {
        int endIndex = (startIndex + subsequenceLength -1) % list.size();
        Object temp;

        //System.out.println("start: "+startIndex);
        //System.out.println("sub len: "+subsequenceLength);

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
    public Representation apply(OptimizationProblem problem, Representation r) {

        Array ni = (Array) r.clone();

        List list = ni.getList();

        int subsequenceLength = shortestMoveLength + RandUtil.randInt(longestMoveLength-shortestMoveLength+1);


        int startIndex = RandUtil.randInt(ni.getLength());

        reverseSubSequence(list,startIndex,subsequenceLength);

        ni.setList(list);
        return (Representation) ni;


     }

    @Override
    public int neighboringCount() {
        return 1;
    }
}
