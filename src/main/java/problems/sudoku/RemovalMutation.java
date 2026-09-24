package problems.sudoku;

import base.OptimizationProblem;
import base.MutationOperator;
import representation.base.Representation;
import util.random.RandUtil;

/**
 * Created by dindar.oz on 02.06.2015.
 */
public class RemovalMutation implements MutationOperator {
    private static final int MAX_ATTEMPT = 10;
    private static final double APPLY_PROBABILITY = 0.4;

    @Override
    public Representation apply(OptimizationProblem problem, Representation i) {

        if (!RandUtil.rollDice(APPLY_PROBABILITY))
            return i.clone();

        SDKProblem sp = (SDKProblem)problem;

        SDKRepresentation si = (SDKRepresentation)i;

        SDKRepresentation mutant = (SDKRepresentation)si.clone();
        int[][] board = mutant.getBoard();

        int blockSize = si.getBlockSize();


        int x = RandUtil.randInt(blockSize*blockSize);
        int y = RandUtil.randInt(blockSize*blockSize);
        if (sp.initialBoard[x][y]==0)
        {
            board[x][y] = 0;
            return mutant;
        }

        return si.clone();
    }

    @Override
    public int neighboringCount() {
        return 1;
    }
}
