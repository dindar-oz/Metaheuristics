package representation;

import representation.base.Array;
import representation.base.Representation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by dindar.oz on 15.07.2015.
 */
public class IntegerAssignment implements Array<Integer>,Representation {

    int values[];

    public int[] getValues() {
        return values;
    }

    public IntegerAssignment(int[] values)
    {
        this.values = new int[values.length];
        for (int i=0; i<values.length;i++)
        {
            this.values[i] = values[i];
        }
    }



    @Override
    public Representation clone() {
        return new IntegerAssignment(Arrays.copyOf(values,values.length));
    }

    @Override
    public double distanceTo(Representation r) {
        IntegerAssignment ia = (IntegerAssignment) r;
        int dif=0;

        for (int i=0;i<values.length;i++)
        {
            if (values[i]!=ia.values[i])
                dif++;
        }
        return dif;
    }


    public Integer get(int i)
    {
        return values[i];
    }

    @Override
    public String toString() {
        return "IntegerAssignment{" +
                "values=" + Arrays.toString(values) +
                '}';
    }




    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IntegerAssignment that = (IntegerAssignment) o;

        if (!Arrays.equals(values, that.values)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }



    @Override
    public List<Integer> getList() {
        return new ArrayList(Arrays.asList(values));
    }

    @Override
    public void setList(List<Integer> values) {

        this.values = new int[values.size()];
        for (int i =0;i<values.size();i++)
        {
            this.values[i] = values.get(i);
        }
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
            if (v.equals(values[i]))
                return true;
        }
        return false;
    }

    @Override
    public int firstOf(Integer v) {
        for (int i=0;i<values.length;i++)
        {
            if (v.equals(values[i]))
                return i;
        }
        return -1;
    }

    @Override
    public Array cloneArray() {
        return (Array)clone();
    }


}
