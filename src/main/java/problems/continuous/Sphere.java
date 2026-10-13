package problems.continuous;

import base.ContinuousProblem;
import representation.DoubleVector;

/**
 * f(x) = sum(x_i^2), x in [-5, 5]^n. Unimodal, minimum 0 at the origin.
 */
public final class Sphere implements ContinuousProblem {
    private final int dimension;

    public Sphere(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public double cost(DoubleVector solution) {
        double sum = 0;
        for (double x : solution.getValues())
            sum += x * x;
        return sum;
    }

    @Override
    public int getDimensionCount() {
        return dimension;
    }

    @Override
    public double getUpperBound(int d) {
        return 5;
    }

    @Override
    public double getLowerBound(int d) {
        return -5;
    }
}
