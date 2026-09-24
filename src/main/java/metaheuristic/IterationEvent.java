package metaheuristic;

import representation.base.Population;
import representation.base.Representation;

/**
 * Created by dindar.oz on 22.06.2015.
 */
public interface IterationEvent {
    long getIterationCount();
    long getNeighboringCount();
    double getBestCost();
    Representation getBestSolution();
    Representation getCurrentSolution();
    double getCurrentCost();
}
