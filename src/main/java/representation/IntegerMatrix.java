package representation;

import representation.base.Representation;

import java.util.Arrays;

/**
 * Created by dindar.oz on 6/23/2017.
 */
public class IntegerMatrix implements Representation {

    int rowCount;

    int matrix[][];

    public IntegerMatrix(int rowCount, int[][] matrix) {
        this.rowCount = rowCount;
        this.matrix = matrix;
    }

    public int get(int r, int c)
    {
        return matrix[r][c];
    }
    public void set(int r, int c, int v)
    {
        matrix[r][c]= v;
    }

    public int getRowCount() {
        return rowCount;
    }



    public int[][] getMatrix() {
        return matrix;
    }

    @Override
    public Representation clone() {

        int m[][] = new int[rowCount][];

        for (int r = 0; r < rowCount; r++) {
            m[r]= Arrays.copyOf(matrix[r],matrix[r].length);
        }
        return new IntegerMatrix(rowCount,m);
    }

    @Override
    public double distanceTo(Representation r) {
        return 0;
    }
}
