package util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Created by oz on 16.07.2015.
 */
public class ArrayUtil {
    public static int getMinIndex(double[] vals) {
        double minVal = vals[0];
        int minIndex = 0;
        for (int i=1;i<vals.length;i++)
        {
            if (vals[i]<minVal)
            {
                minVal = vals[i];
                minIndex = i;
            }
        }
        return minIndex;
    }

    public static List<Integer> indexesOf(int[] values, int value) {
        List<Integer> indexes = new ArrayList<>();
        
        for (int i=0;i<values.length;i++)
        {
            if (values[i]==value)
                indexes.add(i);
        }
        return indexes;
    }

    public static List<Integer> indexesOf(Integer[] values, int value) {
        List<Integer> indexes = new ArrayList<>();

        for (int i=0;i<values.length;i++)
        {
            if (values[i]!= null && values[i].equals(value))
                indexes.add(i);
        }
        return indexes;
    }

    public static int getMaxIndex(double[] vals) {
        double maxVal = vals[0];
        int maxIndex = 0;
        for (int i=1;i<vals.length;i++)
        {
            if (vals[i]>maxVal)
            {
                maxVal = vals[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    public static void arrayCopy(int[][] aSource, int[][] aDestination) {
        for (int i = 0; i < aSource.length; i++) {
            System.arraycopy(aSource[i], 0, aDestination[i], 0, aSource[i].length);
        }
    }

    public static void arrayCopy(int[] aSource, int[] aDestination) {
            System.arraycopy(aSource, 0, aDestination, 0, aSource.length);
    }

    public static int[][] cloneArray(int[][] src) {
        int length = src.length;
        int[][] target = new int[length][];
        for (int i = 0; i < length; i++) {
            target[i] = Arrays.copyOf(src[i],src[i].length);
        }
        return target;
    }

    public static String formatDoubleArray(double[] array, int decimals) {
        if (decimals < 0) throw new IllegalArgumentException("Decimal places must be non-negative.");

        String formatString = "%." + decimals + "f";
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < array.length; i++) {
            sb.append(String.format(Locale.US, formatString, array[i]));
            if (i < array.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
