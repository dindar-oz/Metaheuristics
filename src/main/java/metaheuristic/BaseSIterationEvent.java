package metaheuristic;

import representation.base.Representation;

/**
 * Created by dindar.oz on 25.06.2015.
 */
public class BaseSIterationEvent implements IterationEvent {
    protected final long iterationCount;
    protected final double bestCost;
    protected final Representation bestSolution;
    protected final long neighboringCount;
    protected final Representation currentSolution;
    protected final double currentCost;

    public BaseSIterationEvent(long iterationCount, long neighboringCount,
                               double bestCost,
                               Representation bestSolution,
                               double currentCost,
                               Representation currentSolution) {
        this.iterationCount = iterationCount;
        this.bestCost = bestCost;
        this.bestSolution  =bestSolution;
        this.neighboringCount = neighboringCount;
        this.currentCost= currentCost;
        this.currentSolution= currentSolution;
    }

    public BaseSIterationEvent(long iterationCount, long neighboringCount,
                               double bestCost,
                               Representation bestSolution) {
        this.iterationCount = iterationCount;
        this.bestCost = bestCost;
        this.bestSolution  =bestSolution;
        this.neighboringCount = neighboringCount;
        this.currentCost= 0.0;
        this.currentSolution= null;
    }

    @Override
    public long getIterationCount() {
        return iterationCount;
    }

    @Override
    public long getNeighboringCount() {
        return neighboringCount;
    }

    @Override
    public double getBestCost() {
        return bestCost;
    }

    @Override
    public Representation getBestSolution() {
        return bestSolution;
    }

    public Representation getCurrentSolution() {
        return currentSolution;
    }

    public double getCurrentCost() {
        return currentCost;
    }
}
