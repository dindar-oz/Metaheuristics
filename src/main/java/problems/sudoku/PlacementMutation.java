package problems.sudoku;

import base.MutationOperator;
import util.random.RNG;

/**
 * Created by dindar.oz on 02.06.2015.
 */
public class PlacementMutation implements MutationOperator<SDKRepresentation> {
    private static final int MAX_ATTEMPT = 10;

    private final SDKProblem problem;

    public PlacementMutation(SDKProblem problem) {
        this.problem = problem;
    }

    @Override
    public SDKRepresentation apply(SDKRepresentation r, RNG rng) {

        SDKRepresentation si = r;

        SDKRepresentation mutant = si.copy();
        int[][] board = mutant.getBoard();

        int blockSize = si.getBlockSize();


        for (int attempt = 0; attempt<MAX_ATTEMPT;attempt++ )
        {
            int x = rng.randInt(blockSize*blockSize);
            int y = rng.randInt(blockSize*blockSize);
            if (board [x][y]==0)
            {
                int v =  rng.randInt(blockSize*blockSize)+1;
                board[x][y] = v;
                if (problem.isFeasible(mutant))
                    return mutant;
                board[x][y] = 0;
            }
        }

        return si.copy();
    }
}
