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
public class ReverseAndOrMoveSubSequence implements MutationOperator {

    public static final int DEFAULT_SHORTEST_MOVE = 5;
    public static final int DEFAULT_LONGEST_MOVE = 10;

    int shortestMoveLength =DEFAULT_SHORTEST_MOVE;
    int longestMoveLength = DEFAULT_LONGEST_MOVE;

    public ReverseAndOrMoveSubSequence() {
    }


    public ReverseAndOrMoveSubSequence(int shortestMoveLength, int longestMoveLength) {
        this.shortestMoveLength = shortestMoveLength;
        this.longestMoveLength = longestMoveLength;
    }


    public static List moveSubSequence(List list, int startIndex, int subsequenceLength, int moveLength)
    {
        Object startElement = list.get(startIndex);
        int endIndex = (startIndex + subsequenceLength -1) % list.size();
        Object endElement = list.get(endIndex);

        System.out.println("start: "+startIndex);
        System.out.println("sub len: "+subsequenceLength);
        System.out.println("move len: "+moveLength);


        int removedIndex;
        Object removedElement,lastElement;

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
    public Representation apply(OptimizationProblem problem, Representation r) {

        Array ni = (Array) r.clone();

        List list = ni.getList();

        int subsequenceLength = shortestMoveLength + RandUtil.randInt(longestMoveLength-shortestMoveLength+1);
        int startIndex = RandUtil.randInt(ni.getLength());
        if (RandUtil.rollDice(0.5))
        {
            int moveLength = RandUtil.randInt(ni.getLength());
            ReverseSubSequence.reverseSubSequence(list,startIndex,subsequenceLength);
            MoveSubSequence.moveSubSequence(list,startIndex,subsequenceLength,moveLength);
        }
        else
        {
            if (RandUtil.rollDice(0.5))
            {
                ReverseSubSequence.reverseSubSequence(list,startIndex,subsequenceLength);
            }
            else
            {
                int moveLength = RandUtil.randInt(ni.getLength());
                MoveSubSequence.moveSubSequence(list,startIndex,subsequenceLength,moveLength);
            }
        }

        ni.setList(list);

        return (Representation) ni;

     }

    @Override
    public int neighboringCount() {
        return 1;
    }
}
