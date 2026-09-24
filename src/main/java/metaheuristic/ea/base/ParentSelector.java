package metaheuristic.ea.base;

import base.OptimizationProblem;
import representation.base.Individual;

import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public interface ParentSelector {
    public List<Individual> selectParents(OptimizationProblem problem, List<Individual> population);
}
