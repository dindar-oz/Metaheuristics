package problems.tsp;

import metaheuristic.aco.GraphProblem;
import representation.IntegerPermutation;
import representation.base.Representation;

import java.util.Random;

/**
 * Symmetric travelling salesman problem. A solution is an IntegerPermutation of the cities 0..n-1,
 * its cost is the length of the closed tour.
 */
public class TSPProblem implements GraphProblem {

    private final double[][] distanceMatrix;

    public TSPProblem(double[][] distanceMatrix) {
        this.distanceMatrix = distanceMatrix;
    }

    // Builds the problem from 2D city coordinates using Euclidean distances
    public static TSPProblem fromCoordinates(double[][] xy) {
        int n = xy.length;
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                d[i][j] = Math.hypot(xy[i][0] - xy[j][0], xy[i][1] - xy[j][1]);
        return new TSPProblem(d);
    }

    // Random cities in the unit square
    public static TSPProblem randomEuclidean(int cityCount, long seed) {
        Random random = new Random(seed);
        double[][] xy = new double[cityCount][2];
        for (double[] city : xy) {
            city[0] = random.nextDouble();
            city[1] = random.nextDouble();
        }
        return fromCoordinates(xy);
    }

    public int getCityCount() {
        return distanceMatrix.length;
    }

    @Override
    public int getNodeCount() {
        return getCityCount();
    }

    @Override
    public double getDistance(int from, int to) {
        return distanceMatrix[from][to];
    }

    @Override
    public double cost(Representation r) {
        IntegerPermutation tour = (IntegerPermutation) r;
        int n = tour.getLength();
        double length = 0;
        for (int i = 0; i < n; i++)
            length += distanceMatrix[tour.get(i)][tour.get((i + 1) % n)];
        return length;
    }

    @Override
    public boolean isFeasible(Representation r) {
        if (!(r instanceof IntegerPermutation))
            return false;
        IntegerPermutation tour = (IntegerPermutation) r;
        if (tour.getLength() != getCityCount())
            return false;
        boolean[] visited = new boolean[getCityCount()];
        for (int i = 0; i < tour.getLength(); i++) {
            int city = tour.get(i);
            if (city < 0 || city >= visited.length || visited[city])
                return false;
            visited[city] = true;
        }
        return true;
    }
}
