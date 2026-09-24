package metaheuristic.ea.base;

import base.OptimizationProblem;
import representation.base.Representation;

import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public interface CrossOverOperator {
    public List<Representation> apply(OptimizationProblem problem, Representation p1, Representation p2 );
}
