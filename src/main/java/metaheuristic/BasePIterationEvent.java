package metaheuristic;

import representation.base.Population;
import representation.base.Representation;

/**
 * Created by dindar.oz on 25.06.2015.
 */
public class BasePIterationEvent implements PIterationEvent {
    protected final long iterationCount;
    protected final double bestCost;
    protected final Representation bestSolution;
    protected final long neighboringCount;
    protected final Population currentPopulation;


    public BasePIterationEvent(long iterationCount, long neighboringCount,
                               double bestCost,
                               Representation bestSolution,
                               Population currentPopulation) {
        this.iterationCount = iterationCount;
        this.bestCost = bestCost;
        this.bestSolution  =bestSolution;
        this.neighboringCount = neighboringCount;
        this.currentPopulation= currentPopulation;
    }

    public BasePIterationEvent(long iterationCount, long neighboringCount,
                               double bestCost,
                               Representation bestSolution) {
        this.iterationCount = iterationCount;
        this.bestCost = bestCost;
        this.bestSolution  =bestSolution;
        this.neighboringCount = neighboringCount;
        this.currentPopulation= null;
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

    @Override
    public Population getCurrentPopulation() {
        return currentPopulation;
    }


}
