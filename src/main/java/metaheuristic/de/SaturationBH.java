package metaheuristic.de;

import base.ContinuousProblem;
import util.random.RNG;

public class SaturationBH implements BoundaryHandler{
    @Override
    public void handle(ContinuousProblem cProblem, double[] x, double[] target, RNG rng) {
        for (int d = 0; d < x.length; d++) {
            double lower = cProblem.getLowerBound(d);
            double upper = cProblem.getUpperBound(d);
            x[d] = Math.max(lower, Math.min(upper,x[d]));
        }
    }
}
