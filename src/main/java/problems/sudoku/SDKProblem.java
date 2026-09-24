package problems.sudoku;

import base.OptimizationProblem;
import representation.base.Representation;

/**
 * Created by dindar.oz on 02.06.2015.
 */
public class SDKProblem implements OptimizationProblem {

    private static final double COLLISION_PENALTY = 100;
    private static final double PLACEMENT_BONUS = 50;
    int blockSize=0;
    int [][] initialBoard;

    public SDKProblem(int blockSize, int[][] initialBoard) {
        this.blockSize = blockSize;
        this.initialBoard = initialBoard;
    }


    @Override
    public double cost(Representation r)  {
        double penalty = blockSize*blockSize*blockSize*blockSize* PLACEMENT_BONUS;
        int placement = 0;
        
        SDKRepresentation si = (SDKRepresentation)r;
        int board[][] = si.getBoard();
        
        for (int x = 0; x<blockSize*blockSize ;x++)
        {
            int rowValues[] = new int[blockSize*blockSize+1];
            for (int y = 0; y<blockSize*blockSize ;y++)
            {
                if (board[x][y]>0)
                {
                    rowValues[board[x][y]]++;
                    if (rowValues[board[x][y]]>1)
                        penalty += COLLISION_PENALTY;
                }
            }        
        }
        for (int y = 0; y<blockSize*blockSize ;y++)
        {
            int colValues[] = new int[blockSize*blockSize+1];
            for (int x = 0; x<blockSize*blockSize ;x++)
            {
                if (board[x][y]>0)
                {
                    colValues[board[x][y]]++;
                    if (colValues[board[x][y]]>1)
                        penalty += COLLISION_PENALTY;
                }
            }
        }

        for (int b = 0; b<blockSize*blockSize ;b++)
        {
            int blockValues[] = new int[blockSize*blockSize+1];
            for (int blockIndex = 0; blockIndex<blockSize*blockSize ;blockIndex++)
            {
                int v = blockMember(board,b,blockIndex);
                if (v>0)
                {
                    blockValues[v]++;
                    if (blockValues[v]>1)
                        penalty += COLLISION_PENALTY;
                }
            }
        }

        for (int x = 0; x<blockSize*blockSize ;x++)
        {
            for (int y = 0; y<blockSize*blockSize ;y++)
            {
                if (board[x][y]>0)
                {
                    placement++;
                }
            }
        }

        penalty += placement * -1 * PLACEMENT_BONUS;

        return penalty;
    }

    @Override
    public boolean isFeasible(Representation r)
    {
        SDKRepresentation si = (SDKRepresentation)r;
        int board[][] = si.getBoard();

        for (int x = 0; x<blockSize*blockSize ;x++)
        {
            int rowValues[] = new int[blockSize*blockSize+1];
            for (int y = 0; y<blockSize*blockSize ;y++)
            {
                if (board[x][y]>0)
                {
                    rowValues[board[x][y]]++;
                    if (rowValues[board[x][y]]>1)
                        return false;
                }
            }
        }
        for (int y = 0; y<blockSize*blockSize ;y++)
        {
            int colValues[] = new int[blockSize*blockSize+1];
            for (int x = 0; x<blockSize*blockSize ;x++)
            {
                if (board[x][y]>0)
                {
                    colValues[board[x][y]]++;
                    if (colValues[board[x][y]]>1)
                        return false;
                }
            }
        }

        for (int b = 0; b<blockSize*blockSize ;b++)
        {
            int blockValues[] = new int[blockSize*blockSize+1];
            for (int blockIndex = 0; blockIndex<blockSize*blockSize ;blockIndex++)
            {
                int v = blockMember(board,b,blockIndex);
                if (v>0)
                {
                    blockValues[v]++;
                    if (blockValues[v]>1)
                        return false;
                }
            }
        }

        return true;
    }

    private int blockMember(int[][] board, int b, int blockIndex) {
        int xOffset = (b % blockSize)*blockSize;
        int yOffset = (b/ blockSize)*blockSize;
        
        int x = (blockIndex % blockSize) ;
        int y = (blockIndex/blockSize);
        
        return board[xOffset+x][yOffset+y];
    }
}
