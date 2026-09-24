package representation;

import representation.base.Array;
import representation.base.Permutation;
import representation.base.Representation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public class IntegerPermutation implements Permutation<Integer>,Representation {

    Integer [] values;

    public IntegerPermutation(Integer[] values)
    {
        this.values = values;
    }

    public IntegerPermutation(int[] vList)
    {
        values = new Integer[vList.length];
        for (int i=0;i<vList.length;i++)
        {
            values[i] = new Integer(vList[i]);
        }
    }

    public IntegerPermutation(List<Integer> vList)
    {
        values = vList.toArray(new Integer[vList.size()]) ;
    }


    public Integer[] getValues() {
        return values;
    }


    @Override
    public Representation clone() {
        Integer q[] = new Integer[values.length];
        for (int i =0 ; i<q.length;i++)
        {
            q[i] = (values[i].intValue());
        }
        return new IntegerPermutation(q);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IntegerPermutation that = (IntegerPermutation) o;

        if (!Arrays.equals(values, that.values)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public double distanceTo(Representation r) {
        IntegerPermutation ip = (IntegerPermutation)r;

        int d=0;
        for (int i =0;i<values.length;i++)
        {
            if (!values[i].equals(ip.get(i)))
                d++;
        }

        return (double)d;
    }

    @Override
    public Integer get(int i) {
        return values[i];
    }

    @Override
    public List<Integer> getList() {
        return new ArrayList(Arrays.asList(values));
    }

    @Override
    public void setList(List<Integer> values) {
        this.values = values.toArray(new Integer[0]);
    }

    @Override
    public void set(int i, Integer v) {
        values[i] = v;
    }

    @Override
    public void swap(int i, int j) {

        Integer tmp = values[i];
        values[i] = values[j];
        values[j] = tmp;
    }

    @Override
    public void move(int from, int to) {
        if (from == to)
            return;
        if (from >to )
        {
            for (int i=from ;i>to;i--)
            {
                swap(i,i-1);
            }
        }
        else
        {
            for (int i=from ;i<to;i++)
            {
                swap(i,i+1);
            }
        }
    }

    @Override
    public int getLength() {
        return values.length;
    }

    @Override
    public boolean exists(Integer v) {
        for (int i=0;i<values.length;i++)
        {
            if (values[i].equals(v))
                return true;
        }
        return false;
    }

    @Override
    public int firstOf(Integer v) {
        for (int i=0;i<values.length;i++)
        {
            if (values[i]!=null && values[i].equals(v))
                return i;
        }
        return -1;
    }

    @Override
    public Array cloneArray() {
        return (Array)clone();
    }

    @Override
    public String toString() {
        return "IntegerPermutation{" +
                "values=" + Arrays.toString(values) +'}';
    }

    public boolean isHealthy()
    {
        for (int i= 0 ;i<values.length;i++)
        {
            if (!exists(i+1)) {
                System.out.println((i+1) + " is missing");
                return false;
            }
        }
        return true;
    }
}
