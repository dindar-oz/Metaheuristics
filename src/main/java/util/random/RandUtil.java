package util.random;


import java.util.ArrayList;
import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public class RandUtil {

    static RNG r = new MersenneTwisterRNG();

    public static RNG getDefaultRNG() {
        return r;
    }

    public static void setRNG(RNG rng)
    {
        r = rng;
    }

    // Makes all subsequent random decisions reproducible
    public static void setSeed(long seed)
    {
        r = new MersenneTwisterRNG(seed);
    }

    public static int randInt(int length) {
        if (length==0)
            return 0;
        return r.randInt(length);
    }

    public static boolean rollDice(double a) {
        return  (r.randDouble()<a);
    }

    public static double randDouble() {
        return r.randDouble();
    }

    public static double randDouble(double lb, double ub) {
        return lb + r.randDouble()*(ub-lb);
    }

    // Returns the selected index based on the weights(probabilities)
    public static int rouletteSelect(double[] weight) {
        return rouletteSelect(r,weight);
    }

    public static int rouletteSelect(double[] weight, int len)
    {
        return rouletteSelect(r,weight,len);
    }

    public static int rouletteSelect(RNG rng, double[] weight, int len) {
        // calculate the total weight
        double weight_sum = 0;
        for(int i=0; i<len; i++) {
            weight_sum += weight[i];
        }
        // get a random value
        double value = rng.randDouble() * weight_sum;
        // locate the random value based on the weights
        for(int i=0; i<len; i++) {
            value -= weight[i];
            if(value <= 0) return i;
        }
        // only when rounding errors occur
        return len - 1;
    }


    // Returns the selected index based on the weights(probabilities)
    public static int rouletteSelect(RNG rng, double[] weight) {
        return rouletteSelect(rng,weight,weight.length);
    }


    public static int[] roulletteSelectMulti(RNG rng, double[] weights, int count)
    {
        Roulette roulette = new Roulette(rng,weights);
        int indexes[] = new int[count];
        for (int i = 0; i < count; i++) {
            indexes[i] = roulette.spin();
        }
        return indexes;
    }

    public static int[] roulletteSelectMulti(double[] weights, int beamSize) {
        return roulletteSelectMulti(r,weights,beamSize);
    }

    public static int rouletteSelectInverse(double[] weight, int end) {
        return rouletteSelectInverse(r,weight,end);
    }

    private static int rouletteSelectInverse(RNG r, double[] weight, int end) {
        // calculate the total weight
        double weight_sum = 0;

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for(int i=0; i<end; i++) {
            weight_sum += weight[i];
            if (weight[i]<min)
                min = weight[i];
            if (weight[i]>max)
                max = weight[i];
        }
        // get a random value
        double value = r.randDouble() * weight_sum;

        double t = min+ max;
        // locate the random value based on the weights
        for(int i=0; i<end; i++) {
            value -= (t-weight[i]);
            if(value <= 0) return i;
        }
        // only when rounding errors occur
        return end - 1;
    }

    public static int rouletteSelectInverse(double[] weight) {
        return rouletteSelectInverse(r,weight);
    }



    // Returns the selected index based on the weights(probabilities)
    public static int rouletteSelectInverse(RNG rng, double[] weight) {
        return rouletteSelectInverse(rng,weight,weight.length);
    }

    public static List<Integer> randIntSet(int max, int setSize) {
        return randIntSet(r,max,setSize);
    }

    public static List<Integer> randIntSet(RNG rng, int max, int setSize) {
        List<Integer> intSet = new ArrayList<>();

        if (max<setSize)
            throw new RuntimeException("Impossible Selection");

        for (int i=0;i<setSize;i++)
        {
            int c= randInt(max);
            while (intSet.contains(c))
                c = randInt(max);
            intSet.add(c);
        }
        return intSet;
    }
public static double[] randDoubles(int n, double lb, double ub) {
        double values[] = new double[n];

        for (int i=0;i<n ;i++)
        {
            values[i] = randDouble(lb,ub);
        }
        return values;
    }


}
