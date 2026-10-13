package problems.continuous;

import base.ContinuousProblem;
import representation.DoubleVector;

/**
 * f(x) = 10n + sum(x_i^2 - 10 cos(2 pi x_i)), x in [-5.12, 5.12]^n. Highly multimodal, minimum 0 at the origin.
 */
public final class Rastrigin implements ContinuousProblem {
    private final int dimension;

    public Rastrigin(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public double cost(DoubleVector solution) {
        double sum = 10 * dimension;
        for (double x : solution.getValues())
            sum += x * x - 10 * Math.cos(2 * Math.PI * x);
        return sum;
    }

    @Override
    public int getDimensionCount() {
        return dimension;
    }

    @Override
    public double getUpperBound(int d) {
        return 5.12;
    }

    @Override
    public double getLowerBound(int d) {
        return -5.12;
    }
}
