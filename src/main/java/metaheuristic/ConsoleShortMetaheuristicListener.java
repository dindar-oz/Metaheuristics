package metaheuristic;

/**
 * Created by dindar.oz on 23.06.2015.
 */
public class ConsoleShortMetaheuristicListener implements MetaHeuristicListener {
    @Override
    public void onIterationEvent(IterationEvent event) {
        System.out.println("NC: "+ event.getNeighboringCount()+"  Cost: "+event.getBestCost());
    }
}
