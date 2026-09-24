package metaheuristic.ea.parentselector;

import base.OptimizationProblem;
import metaheuristic.ea.base.ParentSelector;
import representation.base.Individual;
import util.random.RandUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public class RouletteWheelParentSelector implements ParentSelector {



    int parentCount =0;

    public RouletteWheelParentSelector(int parentCount) {
        this.parentCount = parentCount;
    }

    public RouletteWheelParentSelector() {
        this.parentCount = -1;
    }



    Individual selectParent(OptimizationProblem problem, List<Individual> population) {

        double costs[] = new double[population.size()];
        for (int i=0;i<costs.length;i++)
        {
            costs[i] = population.get(i).getCost();
        }

        int index = RandUtil.rouletteSelectInverse(costs);

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
    public List<Individual> selectParents(OptimizationProblem problem, List<Individual> population) {

        List<Individual> copied = copyPopulation(population);

        List<Individual> parents = new ArrayList<>();

        if (parentCount == -1 )
            parentCount = population.size()/2;

        for (int c= 0; c< parentCount;c++)
        {
            Individual parent = selectParent(problem,copied);
            copied.remove(parent);
            parents.add(parent);
        }

        return parents;
    }
}
