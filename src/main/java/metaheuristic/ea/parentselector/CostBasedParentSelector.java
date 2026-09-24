package metaheuristic.ea.parentselector;

import base.OptimizationProblem;
import metaheuristic.ea.base.ParentSelector;
import representation.CostBasedComparator;
import representation.base.Individual;

import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public class CostBasedParentSelector implements ParentSelector {


    int parentCount =-1;

    public CostBasedParentSelector(int parentCount) {
        this.parentCount = parentCount;
    }

    public CostBasedParentSelector() {

    }


    @Override
    public List<Individual> selectParents(OptimizationProblem problem, List<Individual> population) {

        if (parentCount ==-1)
            parentCount = population.size()/2;

        population.sort(new CostBasedComparator());
        List<Individual> parents = population.subList(0,parentCount);

        return parents;
    }
}
