package representation;

import representation.base.Individual;

import java.util.Comparator;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public class CostBasedComparator implements Comparator<Individual> {
    @Override
    public int compare(Individual o1, Individual o2) {
        return Double.compare(o1.getCost(),o2.getCost());
    }
}
