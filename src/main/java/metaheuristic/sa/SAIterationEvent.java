package metaheuristic.sa;

import metaheuristic.BaseSIterationEvent;
import representation.base.Representation;

/**
 * Created by dindar.oz on 22.06.2015.
 */
public class SAIterationEvent extends BaseSIterationEvent {

    private final double temperature;


    public SAIterationEvent(long iterationCount, long neighboringCount,double currentTemperature, double bestCost, Representation bestSolution, double currentCost, Representation currentSolution) {
        super(iterationCount,neighboringCount,bestCost,bestSolution, currentCost, currentSolution);
        this.temperature= currentTemperature;
    }

    @Override
    public String toString() {
        return "SAIterationEvent{" +
                "iterationCount=" + iterationCount +
                ", neighboringCount=" + neighboringCount +
                ", bestCost=" + bestCost +
                ", bestSolution=" + bestSolution +
                ", temperature=" + temperature +
                '}';
    }
}
