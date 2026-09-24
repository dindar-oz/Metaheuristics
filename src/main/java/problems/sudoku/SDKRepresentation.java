package problems.sudoku;

import representation.base.Representation;



/**
 * Created by dindar.oz on 02.06.2015.
 */
public class SDKRepresentation implements Representation{

    int blockSize = 3;
    int board[][] = null;

    public SDKRepresentation(int blockSize, int[][] board) {
        this.blockSize = blockSize;
        this.board = board;
    }


    public int getBlockSize() {
        return blockSize;
    }

    public int[][] getBoard() {
        return board;
    }

    @Override
    public Representation clone() {
        int b[][] = new int[board.length][];

        for (int i=0;i<b.length;i++)
        {
            b[i] = new int[board[i].length];
            for (int j = 0 ;j<b[i].length;j++)
                b[i][j]= board[i][j];
        }

        return new SDKRepresentation(blockSize,b);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.deepHashCode(board);
    }

    @Override
    public boolean equals(Object r) {
        if (!(r instanceof SDKRepresentation))
        return false;

        SDKRepresentation sr = (SDKRepresentation)r;

        if (sr.blockSize != blockSize)
            return false;

        for (int x =0 ; x< blockSize*blockSize; x++)
        {
            for (int y =0 ; y< blockSize*blockSize; y++)
            {
                if (board[x][y]!= sr.board[x][y])
                    return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        String st ="";

        for (int x =0 ; x< blockSize*blockSize; x++)
        {
            if (x>0 && x%blockSize ==0)
                st += "\n";

            for (int y =0 ; y< blockSize*blockSize; y++)
            {
                if (y>0 && y%blockSize==0 )
                    st += "\t";

                st += board[x][y] + "\t";
            }
            st += "\n";
        }
        return st;
    }
}
