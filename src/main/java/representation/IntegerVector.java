package representation;

import representation.base.Array;
import representation.base.Representation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by dindar.oz on 15.07.2015.
 */
public class IntegerVector implements Array<Integer>,Representation {

    List<Integer>  values;

    public List<Integer> getValues() {
        return values;
    }

    public IntegerVector(List<Integer> values)
    {
        this.values = values;
    }

    @Override
    public Representation clone() {
        List<Integer> newList = new ArrayList<>();
        for (Integer i:values)
        {
            newList.add(new Integer(i));
        }
        return new IntegerVector(newList);
    }

    @Override
    public double distanceTo(Representation r) {
        IntegerVector ia = (IntegerVector) r;
        int dif=0;

        for (Integer i: values)
        {
            if (!ia.values.contains(i))
                dif++;
        }
        for (Integer i: ia.values)
        {
            if (!values.contains(i))
                dif++;
        }
        return dif;
    }


    public Integer get(int i)
    {
        return values.get(i);
    }

    @Override
    public String toString() {
        return "IntegerVector{" +
                "values=" + Arrays.toString(values.toArray()) +
                '}';
    }




    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IntegerVector that = (IntegerVector) o;

        if (values.size() != that.values.size())
            return false;

        for (int i=0;i<values.size();i++)
        {
            if (!values.get(i).equals(that.values.get(i)))
                return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values.toArray());
    }



    @Override
    public List<Integer> getList() {
        return values;
    }

    @Override
    public void setList(List<Integer> values) {

        this.values = values;
    }

    @Override
    public void set(int i, Integer v) {
        values.set(i,v);
    }

    @Override
    public void swap(int i, int j) {

        Integer tmp = values.get(i);
        values.set(i, values.get(j));
        values.set(j, tmp);
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
        return values.size();
    }

    @Override
    public boolean exists(Integer v) {
        for (int i=0;i<values.size();i++)
        {
            if (v.equals(values.get(i)))
                return true;
        }
        return false;
    }

    @Override
    public int firstOf(Integer v) {
        for (int i=0;i<values.size();i++)
        {
            if (v.equals(values.get(i)))
                return i;
        }
        return -1;
    }

    @Override
    public Array cloneArray() {
        return (Array)clone();
    }


}
