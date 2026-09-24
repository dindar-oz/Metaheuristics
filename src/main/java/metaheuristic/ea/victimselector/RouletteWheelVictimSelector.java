package metaheuristic.ea.victimselector;

import base.OptimizationProblem;
import metaheuristic.ea.base.VictimSelector;
import representation.base.Individual;
import util.random.RandUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public class RouletteWheelVictimSelector implements VictimSelector {

    public static int DEFAULT_PARENT_COUNT = 2;



    public RouletteWheelVictimSelector() {

    }



    Individual selectVictim(OptimizationProblem problem, List<Individual> population) {

        double costs[] = new double[population.size()];
        for (int i=0;i<costs.length;i++)
        {
            costs[i] = population.get(i).getCost();
        }

        int index = RandUtil.rouletteSelect(costs);

        return population.get(index);
    }

    List<Individual> copyPopulation( List<Individual> population)
    {
        List<Individual> copied = new ArrayList<>();
        for (Individual i:population)
            copied.add(i);

        return copied;
    }

    @Override
    public List<Individual> selectVictims(OptimizationProblem problem, List<Individual> population,int victimCount) {

        List<Individual> copied = copyPopulation(population);

        List<Individual> parents = new ArrayList<>();

        for (int c= 0; c< victimCount;c++)
        {
            Individual victim = selectVictim(problem, copied);
            copied.remove(victim);
            parents.add(victim);
        }

        return parents;
    }
}
