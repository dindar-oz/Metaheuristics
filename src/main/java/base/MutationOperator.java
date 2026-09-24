package base;

import base.OptimizationProblem;
import representation.base.Representation;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public interface MutationOperator {
    Representation apply(OptimizationProblem problem, Representation i);
    default int neighboringCount() {return 1;};

}
