package problems.sudoku;

import base.MutationOperator;
import util.random.RNG;

/**
 * Created by dindar.oz on 02.06.2015.
 */
public class RemovalMutation implements MutationOperator<SDKRepresentation> {
    private static final int MAX_ATTEMPT = 10;
    private static final double APPLY_PROBABILITY = 0.4;

    private final SDKProblem problem;

    public RemovalMutation(SDKProblem problem) {
        this.problem = problem;
    }

    @Override
    public SDKRepresentation apply(SDKRepresentation i, RNG rng) {

        if (!rng.rollDice(APPLY_PROBABILITY))
            return i.copy();

        SDKRepresentation si = i;

        SDKRepresentation mutant = si.copy();
        int[][] board = mutant.getBoard();

        int blockSize = si.getBlockSize();


        int x = rng.randInt(blockSize*blockSize);
        int y = rng.randInt(blockSize*blockSize);
        if (problem.initialBoard[x][y]==0)
        {
            board[x][y] = 0;
            return mutant;
        }

        return si.copy();
    }
}
