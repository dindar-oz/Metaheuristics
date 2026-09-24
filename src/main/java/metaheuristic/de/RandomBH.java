package metaheuristic.de;

import util.random.RandUtil;

import base.ContinuousProblem;


public class RandomBH implements BoundaryHandler{
    @Override
    public void handle(ContinuousProblem cProblem, double[] x, double[] target) {
        for (int d = 0; d < x.length; d++) {
            double lower = cProblem.getLowerBound(d);
            double upper = cProblem.getUpperBound(d);
            if (x[d] < lower || x[d]>upper ) {
                x[d] = lower + RandUtil.randDouble()*(upper-lower);
            }
        }
    }
}
