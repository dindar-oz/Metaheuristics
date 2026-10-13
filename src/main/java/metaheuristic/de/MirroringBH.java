package metaheuristic.de;

import base.ContinuousProblem;
import util.random.RNG;

/**
 * Reflects out-of-bounds coordinates back into the box (repeatedly, as if bouncing between the bounds).
 * Non-finite coordinates are replaced by the target's coordinate.
 */
public class MirroringBH implements BoundaryHandler {
    @Override
    public void handle(ContinuousProblem cProblem, double[] x, double[] target, RNG rng) {
        for (int d = 0; d < x.length; d++) {
            double lower = cProblem.getLowerBound(d);
            double upper = cProblem.getUpperBound(d);
            if (x[d] >= lower && x[d] <= upper)
                continue;
            if (!Double.isFinite(x[d])) {
                x[d] = target[d];
                continue;
            }
            double width = upper - lower;
            double y = (x[d] - lower) % (2 * width);
            if (y < 0)
                y += 2 * width;
            x[d] = lower + (y <= width ? y : 2 * width - y);
        }
    }
}
