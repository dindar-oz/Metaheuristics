package metaheuristic.de;

import base.ContinuousProblem;

public class MirroringBH implements BoundaryHandler{
    @Override
    public void handle(ContinuousProblem cProblem, double[] x, double[] target) {
        for (int d = 0; d < x.length; d++) {
            double lower = cProblem.getLowerBound(d);
            double upper = cProblem.getUpperBound(d);
            while ( x[d] > upper || x[d] < lower )
            {
                x[d]= lower + Math.abs(x[d]-lower);
                x[d]= upper - Math.abs(x[d]-upper);
            }
        }
    }
}
