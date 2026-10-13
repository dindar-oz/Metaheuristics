package metaheuristic.de;

import base.ContinuousProblem;
import util.random.RNG;

public interface BoundaryHandler {
    void handle(ContinuousProblem cProblem, double[] x, double[] target, RNG rng);
}
