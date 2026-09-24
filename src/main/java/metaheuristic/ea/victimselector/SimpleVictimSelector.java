package metaheuristic.ea.victimselector;

import base.OptimizationProblem;
import metaheuristic.ea.base.VictimSelector;
import representation.CostBasedComparator;
import representation.base.Individual;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dindar.oz on 11.06.2015.
 */
public class SimpleVictimSelector implements VictimSelector {
    @Override
    public List<Individual> selectVictims(OptimizationProblem problem, List<Individual> population, int victimCount) {
        population.sort(new CostBasedComparator());
        List<Individual> victims = new ArrayList<>(victimCount);
        for (int i = 1; i <= victimCount; i++) {
            victims.add(population.get(population.size()-i));
        }
        return victims;
    }
}
