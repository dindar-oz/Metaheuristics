package util;


import representation.base.Individual;

import java.util.Collection;
import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public class PopulationUtil {
    public  static double bestFitness(List<Individual> population)
    {
        return bestIndividual(population).getCost();
    }

    public  static double averageFitness(Collection<Individual> population)
    {
        if (population.size()==0)
            return 0;
        double avg = 0;
        for (Individual i:population)
        {
            avg += i.getCost();
        }
        return (int) avg/population.size();
    }

    public static Individual bestIndividual(Collection<Individual> population) {
        Individual best = null;
        for (Individual i:population )
        {
            if (best == null || i.getCost()<best.getCost())
                best = i;
        }
        return best;
    }

}
