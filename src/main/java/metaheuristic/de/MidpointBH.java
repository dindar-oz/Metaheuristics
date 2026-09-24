package metaheuristic.de;

import base.ContinuousProblem;

public class MidpointBH implements BoundaryHandler{
    @Override
    public void handle(ContinuousProblem cProblem, double[] x, double[] target) {
        for (int d = 0; d < x.length; d++) {
            double lower = cProblem.getLowerBound(d);
            double upper = cProblem.getUpperBound(d);

            if (x[d] <lower)
                x[d]= (target[d] + lower)/2;
            if (x[d] >upper)
                x[d]= (target[d] + upper)/2;
        }
    }
}
