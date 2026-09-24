package metaheuristic.de;

import base.ContinuousProblem;
import representation.DoubleVector;

public interface BoundaryHandler {
    void handle(ContinuousProblem cProblem, double[] x, double[] target);
}
