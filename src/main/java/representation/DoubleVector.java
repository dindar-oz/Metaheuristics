package representation;

import representation.base.Array;
import representation.base.Representation;
import util.ArrayUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;


/**
 * Created by dindar.oz on 03.06.2015.
 */
public class DoubleVector implements Representation, Array<Double> {

    double[] values;

    public DoubleVector(int size) {
        this.values = new double[size];
    }


    public DoubleVector(double[] q) {
        this.values = q;
    }

    public DoubleVector(int size, double initialPosition) {
        this.values = new double[size];
        for (int i= 0 ; i<size;i++)
            values[i] = initialPosition;

    }

    public int size()
    {
        return values.length;
    }

    @Override
    public Representation clone() {
        double q[] = new double[values.length];
        for (int i =0 ; i<q.length;i++)
        {
            q[i] = values[i];
        }
        return new DoubleVector(q);
    }

    @Override
    public boolean equals(Object r) {
        if (!(r instanceof DoubleVector))
            return false;
        DoubleVector ir = (DoubleVector)r;

        if (ir.getValues().length!=values.length)
            return false;
        for (int i=0;i<values.length;i++)
        {
            if (values[i] != ir.getValues()[i])
                return false;
        }
        return true;
    }

    @Override
    public double distanceTo(Representation r) {

        DoubleVector dv = (DoubleVector)r;
        double sum =0;


        for (int i=0; i<values.length;i++)
        {
            sum += (values[i]-dv.getValues()[i])*(values[i]-dv.getValues()[i]);
        }


        return Math.sqrt(sum);
    }

    public double[] getValues() {
        return values;
    }

    public void add(DoubleVector v) {
        add(v.getValues());
    }
    public void add(double[] values) {
        for (int i=0;i<values.length;i++)
            this.values[i] += values[i];
    }

    public void setValues(double[] values) {
        this.values = values;
    }

    public void set(int index,double value)
    {
        values[index] = value;
    }

    @Override
    public Double get(int i) {
        return values[i];
    }

    @Override
    public List<Double> getList() {
        return DoubleStream.of(values).boxed().collect(Collectors.toList());
    }

    @Override
    public void setList(List<Double> values) {
        this.values = values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    @Override
    public void set(int i, Double v) {
        set(i,v.doubleValue());
    }

    @Override
    public void swap(int i, int j) {
        double tmp= values[i];
        values[i]= values[j];
        values[j] = values[i];
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
    public boolean exists(Double v) {
        for (int i = 0; i < values.length; i++) {
            if (values[i]== v)
                return true;
        }
        return false;
    }

    @Override
    public int firstOf(Double v) {
        for (int i = 0; i < values.length; i++) {
            if (values[i]== v)
                return i;
        }
        return -1;
    }

    @Override
    public Array<Double> cloneArray() {
        return (Array<Double>) clone();
    }

    @Override
    public String toString() {
        return ArrayUtil.formatDoubleArray(values,3);
    }
}
