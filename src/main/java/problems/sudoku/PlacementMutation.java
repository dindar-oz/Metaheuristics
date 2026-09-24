package problems.sudoku;

import base.OptimizationProblem;
import base.MutationOperator;
import representation.base.Representation;
import util.random.RandUtil;

/**
 * Created by dindar.oz on 02.06.2015.
 */
public class PlacementMutation implements MutationOperator {
    private static final int MAX_ATTEMPT = 10;

    @Override
    public Representation apply(OptimizationProblem problem, Representation r) {

        SDKRepresentation si = (SDKRepresentation)r;

        SDKRepresentation mutant = (SDKRepresentation)si.clone();
        int[][] board = mutant.getBoard();

        int blockSize = si.getBlockSize();


        for (int attempt = 0; attempt<MAX_ATTEMPT;attempt++ )
        {
            int x = RandUtil.randInt(blockSize*blockSize);
            int y = RandUtil.randInt(blockSize*blockSize);
            if (board [x][y]==0)
            {
                int v =  RandUtil.randInt(blockSize*blockSize)+1;
                board[x][y] = v;
                if (problem.isFeasible(mutant))
                    return mutant;
                board[x][y] = 0;
            }
        }

        return si.clone();
    }

    @Override
    public int neighboringCount() {
        return 1;
    }
}
