package metaheuristic.ea.base;

import base.OptimizationProblem;
import representation.base.Individual;

import java.util.List;

/**
 * Created by dindar.oz on 11.06.2015.
 */
public interface VictimSelector {
    List<Individual> selectVictims(OptimizationProblem problem, List<Individual> population,int victimCount);
}
