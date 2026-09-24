package metaheuristic;

import representation.base.Population;
import representation.base.Representation;

/**
 * Created by dindar.oz on 22.06.2015.
 */
public interface PIterationEvent extends IterationEvent{
    Population getCurrentPopulation();

    @Override
    default Representation getCurrentSolution() {
        return getCurrentPopulation().getBest().getRepresentation();
    }

    @Override
    default double getCurrentCost() {
        return getCurrentPopulation().getBestCost();
    }
}
